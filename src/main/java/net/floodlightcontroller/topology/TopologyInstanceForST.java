package net.floodlightcontroller.topology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.python.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import net.floodlightcontroller.multicast.IGMPCapture;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Link;


public class TopologyInstanceForST{
//	protected Map<Integer, SteinerTree> sterinTrees;
    protected static Logger log = LoggerFactory.getLogger(TopologyInstanceForST.class);

    protected TopologyInstance currentInstance;
	protected Map<Long, BroadcastTree> destinationRootedTrees ;
	protected Map<Long, Map<Long, Integer>> metricSpaceDist;

	Map<Link, Integer> linkCost = new HashMap<Link, Integer>(); //cost functions for physical topology link
	protected SteinerTree steinerTree;
	protected SteinerTree oldsteinerTree;
//  protected Map<SwitchPair, Integer> distance;

	public final static int ADD = 1;
	public final static int REMOVE=2;
	public final static double epsilon =  0.5;
	protected int round=0;
	protected ArrayList<Integer> treeOptCost;
//	protected ArrayList<Integer> lt;
	
	 private Integer getDist(Long switchid, Long rootid, HashMap<Long, Integer> metric, BroadcastTree tree){
	    	if(switchid == null || switchid.equals(rootid)){
	    		return 0;
	    	}
	    	
	    	if(metric.containsKey(switchid) == true){
	    		return metric.get(switchid);
	    	}else{
	    		Link link = tree.getTreeLink(switchid);
	        	Long neighbour =  link.getDst();
	        	int cost = tree.getCost(switchid);
	        	return cost + getDist(neighbour, rootid, metric, tree);
	    	}
	    }
	 
	public TopologyInstanceForST(TopologyInstance nt){
		destinationRootedTrees = nt.destinationRootedTrees;
		Set<Cluster>  clusters = nt.clusters;
		Map<Long, Set<Short>> switchPorts = nt.switchPorts; //ports facing switches not hosts
		Map<NodePortTuple, Set<Link>> switchPortLinks = nt.switchPortLinks;
//		Set<NodePortTuple> tunnelPorts = nt.tunnelPorts; //null 
		//metric cost. Shortest Path cost between any pair of node
		metricSpaceDist = new HashMap<Long, Map<Long, Integer>>();
		for(Cluster c: clusters) {
            for (Long node : c.links.keySet()){
            	BroadcastTree tree = destinationRootedTrees.get(node);
        		HashMap<Long, Integer> metric = new HashMap<Long, Integer>();
                for(Long switchid: tree.getLinks().keySet()){
                	if(switchid == node) continue;
                	metric.put(switchid, getDist(switchid, node, metric, tree));
                }
                metricSpaceDist.put(node, metric);
            }
		}
		//other attributes of a ST instance
		steinerTree = new SteinerTree();
		oldsteinerTree = null;
		int tunnel_weight = switchPorts.size() + 1;
		treeOptCost = new ArrayList<Integer>();  
	    for(NodePortTuple npt: switchPortLinks.keySet()) {
	        if (switchPortLinks.get(npt) == null) continue;
	        for(Link link: switchPortLinks.get(npt)) {
	            if (link == null) continue;
	            linkCost.put(link, tunnel_weight);
	        }
	    }
//	    lt = new ArrayList<Integer>();
	}
	
//	TopologyInstanceForSteinerTree(Map<Link, Integer> linkCost){
//		this.steinerTree = new SteinerTree();
//		treeOptCost = new ArrayList<Integer>();
//		this.linkCost = linkCost;
//	    lt = new ArrayList<Integer>();
//	}
	
	public HashMap<Long, HashSet<Link>> getLinkMap(){
		return steinerTree.getLinkMap();
	}
	
	public void updateST(Long switchID, int request){
		switch(request){
		case ADD:
			oldsteinerTree = steinerTree;
			steinerTree.add(switchID);
			break;
		case REMOVE:
			oldsteinerTree = steinerTree;
			steinerTree.remove(switchID);
			break;
		}
		
		//store tree cost into record
		if(round == 0){
			treeOptCost.add(0);
		}
		else{
			int tmpOptCost = steinerTree.treeCost> treeOptCost.get(round-1)?
					steinerTree.treeCost:
					treeOptCost.get(round-1);
			treeOptCost.add(tmpOptCost);	
		}
		round++;
	}
	
	public ArrayList<Link> getAddLinks() {
		// TODO Auto-generated method stub
		return steinerTree.getAddLinks();
	}

	public ArrayList<Link> getRemoveLinks() {
		// TODO Auto-generated method stub
		return steinerTree.getRemoveLinks();
	}
	
	public Set<Long> getTerminals(){
		return steinerTree.terminals;
	}
	
	public void printSTTpo(){
		log.info("---print Steiner Tree topology after round {}:", round - 1);
		log.info("terminals: {}", steinerTree.terminals);
		
		log.info("---Edges:");
		for(Long terminal: steinerTree.terminals){
			log.info(terminal+": {}", steinerTree.metricTreeG.get(terminal));
		}
		log.info("---end----");

	}
	private class SteinerTree {
		private class MetricEdge implements Comparable<MetricEdge>{
			Long src;
			Long dest;
			ArrayList<Link> actualPath;
			int metricCost;
			public MetricEdge(Long src, Long dest, ArrayList<Link> acttualPath,
					int metricCost) {
				super();
				this.src = src;
				this.dest = dest;
				this.actualPath = acttualPath;
				this.metricCost = metricCost;
			}
			
			@Override	
			public String toString(){
				return src+"---"+dest+ "%"+ metricCost +"%" +"  ";
			}
			
			public  MetricEdge getSymmetricEdge(){
				return new MetricEdge(dest,src, 
										actualPath,metricCost);
											
			}
			
			@Override
		    public boolean equals(Object obj){
				if (!(obj instanceof MetricEdge))
			            return false;
				if (obj == this)
			            return true;
				MetricEdge other = (MetricEdge) obj;
				if(src == other.src && dest == other.dest){
						return true;
				}
				if(src == other.dest && dest == other.src){
						return true;
				}
				return false;
			        
			}
			
			@Override
			public int compareTo(MetricEdge other) {
				// TODO Auto-generated method stub
//				if(src == other.src && dest == other.dest){
//					return 0;
//				}
//				if(src == other.dest && dest == other.src){
//					return 0;
//				}
				return -metricCost + other.metricCost;
			}
			
			Long getOtherEnd(Long v){
				return v == src? dest: src;
			}
			
			boolean isSrc(Long v){
				return v == src;
			}
		}
		
		protected Map<Long, ArrayList<MetricEdge>> metricTreeG; //tree edges
		protected Set<Long> terminals; //current terminals
//		protected Map<Long, ArrayList<Long>> neighbourMap; //{a-->b,c; b-->a;}
		protected int treeCost; //currentTree cost
		protected HashMap<Integer, MetricEdge> gs;
		protected int l;  //first round that is larger than epsilon currentOPT
		
//		protected ArrayList<Link> addedLink;
//		protected ArrayList<Link> removedLink;
//		protected Map<Long, ArrayList<Link>> normTreeG;

		public SteinerTree(){
			metricTreeG = new HashMap<Long, ArrayList<MetricEdge>>();
//			neighbourMap = new HashMap<Long, ArrayList<Long>>();
//			normTreeG = new HashMap<Long, ArrayList<Link>>();
//			g = new HashMap<Integer, List<List<Link>>>();
			gs = new HashMap<Integer, MetricEdge>();
			terminals = new HashSet<Long>();
//			addedLink = new ArrayList<Link>();
//			removedLink = new ArrayList<Link>() ;
		}
		
		//convert metric space edges into physical space link
		public HashMap<Long, HashSet<Link>> getLinkMap(){
				HashMap<Long, HashSet<Link>> linkMap = new 
						HashMap<Long, HashSet<Link>>();
				
				for(Long nodeid: metricTreeG.keySet()){
					for(MetricEdge edge: metricTreeG.get(nodeid)){
						for(Link link: edge.actualPath){
							Long node1 = link.getSrc();
							if(linkMap.containsKey(node1)!=true){
								linkMap.put(node1, new HashSet<Link>());
							}
							HashSet<Link> node1Links = linkMap.get(node1);
							node1Links.add(link);
							
							Long node2 = link.getDst();
							if(linkMap.containsKey(node2)!=true){
								linkMap.put(node2, new HashSet<Link>());
							}
							HashSet<Link> node1Links2 = linkMap.get(node2);
							node1Links2.add(link.getSymLink());
						}
					}
				}
				
				return linkMap;
		}
		public ArrayList<Link> getRemoveLinks() {
			// TODO Auto-generated method stub
			HashSet<Link> st = new HashSet();
//			for(Long node: steinerTree.normTreeG.keySet()){
//				st.addAll(steinerTree.normTreeG.get(node));
//
//			}
			if(oldsteinerTree == null || oldsteinerTree.metricTreeG == null || oldsteinerTree.metricTreeG.size()==0){
				return null;
			}
			for(Long node: steinerTree.metricTreeG.keySet()){
				for(MetricEdge edge:steinerTree.metricTreeG.get(node)){
					st.addAll(edge.actualPath);
				}
			}
			HashSet<Link> oldst = new HashSet<Link>();
//			for(Long node: oldsteinerTree.normTreeG.keySet()){
//				st.addAll(oldsteinerTree.normTreeG.get(node));
//			}
			for(Long node: oldsteinerTree.metricTreeG.keySet()){
				for(MetricEdge edge:oldsteinerTree.metricTreeG.get(node)){
					oldst.addAll(edge.actualPath);
				}
			}
			oldst.removeAll(st);
			return Lists.newArrayList(oldst);
		}

		public ArrayList<Link> getAddLinks() {
			// TODO Auto-generated method stub
//			HashSet<Link> st = new HashSet();
//			for(Long node: steinerTree.normTreeG.keySet()){
//				st.addAll(steinerTree.normTreeG.get(node));
//
//			}
//			HashSet<Link> oldst = new HashSet<Link>();
//			for(Long node: oldsteinerTree.normTreeG.keySet()){
//				st.addAll(oldsteinerTree.normTreeG.get(node));
//			}
//			st.removeAll(oldst);
//			return Lists.newArrayList(st);
			// TODO Auto-generated method stub
			HashSet<Link> st = new HashSet();
//						for(Long node: steinerTree.normTreeG.keySet()){
//							st.addAll(steinerTree.normTreeG.get(node));
//
//						}
			if(steinerTree == null || steinerTree.metricTreeG == null || steinerTree.metricTreeG.size() == 0){
				return null;
			}
			for(Long node: steinerTree.metricTreeG.keySet()){
				for(MetricEdge edge:steinerTree.metricTreeG.get(node)){
					st.addAll(edge.actualPath);
				}
			}
			HashSet<Link> oldst = new HashSet<Link>();
//						for(Long node: oldsteinerTree.normTreeG.keySet()){
//							st.addAll(oldsteinerTree.normTreeG.get(node));
//						}
			for(Long node: oldsteinerTree.metricTreeG.keySet()){
				for(MetricEdge edge:oldsteinerTree.metricTreeG.get(node)){
					oldst.addAll(edge.actualPath);
				}
			}
			st.removeAll(oldst);
			return Lists.newArrayList(st);
		}

		public void add(Long switchID) {
			// TODO Auto-generated method stub
//			addedLink.clear();
//			removedLink.clear();
			if(terminals.size() ==  0){
				terminals.add(switchID);
				treeCost = 0;
				gs.put(round, null);
				l = 0; 
//				lt.add(treeCost);
				return ;
			}
			if(terminals.contains(switchID)){
				return;
			}
			
			HashMap<Long, Integer> metric = (HashMap<Long, Integer>) metricSpaceDist.get(switchID);
//			terminals.add(switchID);
			
			//find shortest path to the tree from new terminal
			int shortestMetricDist = Integer.MAX_VALUE;
			Long other=(long) 0;
			for(Long terminal: terminals){
				int distance = metric.get(terminal);
				if(distance < shortestMetricDist){
					shortestMetricDist = distance;
					other = terminal;
					gs.put(round, getPath(terminal, switchID));
				}
			}
			treeCost = treeCost + shortestMetricDist;
//			treeOptCost.add(treeCost);
			
			MetricEdge edge = getPath(other, switchID);
			MetricEdge edge2 = getPath(switchID, other);
			metricTreeG.put(switchID, new ArrayList<MetricEdge>());
			metricTreeG.get(switchID).add(edge2);
			metricTreeG.put(other, new ArrayList<MetricEdge>());
			metricTreeG.get(other).add(edge);
			gs.put(round, edge);
//			for(Link link: edge.actualPath){
//				Long src = link.getSrc();
//				Long dst = link.getDst();
////				ArrayList<Link> srclink = normTreeG.get(src);
////				if(srclink == null)
////					srclink = new ArrayList<Link>();
////				srclink.add(link);
////				normTreeG.put(src, srclink);
////				ArrayList<Link> dstlink = normTreeG.get(dst);
//				if(dstlink == null)
//					dstlink = new ArrayList<Link>();
//				dstlink.add(link);
//				normTreeG.put(dst, dstlink);
//			}
			//temporarily set current tree cost
			
			
			//get lt[round]
			l=0;
			for(int i=round-1;i>=0;i--){
				if (treeCost*epsilon > treeOptCost.get(i)){
					l = i + 1;
//					lt.add(l);
					break;
				};
			}
			
			//get removable edges
			ArrayList<MetricEdge> removableEdges  = new ArrayList<MetricEdge>();
			for(int i=l; i<= round-1; i++){
				if(gs.get(i) == null)
					continue; //this is an edge that has been deleted
				if(gs.get(i).metricCost > epsilon * treeOptCost.get(round-1)/(round-l))
					removableEdges.add(gs.get(i));
			}
			Collections.sort(removableEdges);
			
			//get edges that can replace the removable edges
			for(MetricEdge removeEdge: removableEdges){
				ArrayList<Long> leftTree = new ArrayList<Long>();
				ArrayList<Long> rightTree = new ArrayList<Long>();

				Long src = removeEdge.src;
				LinkedList<Long> toexplore = new LinkedList<Long>();
				HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
				toexplore.add(src);
				while(toexplore.size()!= 0){
					Long switchid = toexplore.getFirst();
					if(seen.containsKey(switchid) == true) continue;
					seen.put(switchid, true);
					ArrayList<MetricEdge> metricEdges = metricTreeG.get(switchid);
					for(MetricEdge me: metricEdges){
						toexplore.add(me.dest);
						leftTree.add(me.dest);
					}
				}
				
				Long dest= removeEdge.dest;
				toexplore.clear();
				seen.clear();
				toexplore.add(dest);
				while(toexplore.size()!= 0){
					Long switchid = toexplore.getFirst();
					if(seen.containsKey(switchid) == true) continue;
					seen.put(switchid, true);
					ArrayList<MetricEdge> metricEdges = metricTreeG.get(switchid);
					for(MetricEdge me: metricEdges){
						toexplore.add(me.dest);
						rightTree.add(me.dest);
					}
				}
				
				for(Long leftnode: leftTree){
					for(Long rightnode: rightTree){
						if(metricSpaceDist.get(leftnode).get(rightnode)
								*(1+epsilon) < removeEdge.metricCost){
							metricTreeG.get(removeEdge.src).remove(removeEdge);
							metricTreeG.get(removeEdge.dest).remove(removeEdge);
//							for(Link link: removeEdge.actualPath){
//								normTreeG.get(link.getSrc()).remove(link);
//								normTreeG.get(link.getDst()).remove(link);
//							}
							
							MetricEdge addEdge = getPath(leftnode,rightnode);
							MetricEdge addEdge2 = getPath(rightnode,leftnode);
							metricTreeG.get(leftnode).add(addEdge);
							metricTreeG.get(rightnode).add(addEdge2);
//							for(Link link: addEdge.actualPath){
//								normTreeG.get(link.getSrc()).add(link);
//								normTreeG.get(link.getDst()).add(link);
//							}
							for(int i: gs.keySet()){
								if(gs.get(i).equals(removeEdge)){
									gs.put(i, addEdge);
								}
							}
							treeCost = treeCost - removeEdge.metricCost + addEdge.metricCost;
						}
					}
				}
			}
//			ArrayList<Link> path = getShoretestPathToTree(
//											switchID, 
//											destinationRootedTrees.get(switchID));
//			gs.put(round, path);
			terminals.add(switchID);
		}
		
		public void remove(Long switchID) {
			// TODO Auto-generated method stub
//			addedLink.clear();
//			removedLink.clear();
			if(terminals.size() ==  0){
				return ;
			}
			terminals.remove(switchID);

			toExtensionTree(switchID);
//			/doSwapEdge()
		}
		
		private void toExtensionTree(Long switchID){
			ArrayList<MetricEdge> meList =  metricTreeG.get(switchID);
			if(meList == null)
				return;
			switch(meList.size()){
				case 0:
					break;
				case 1:
					MetricEdge me = meList.get(0);
					metricTreeG.get(me.dest).
									remove(me.getSymmetricEdge()); //see if directly delete is okay
					metricTreeG.remove(switchID);
//					for(Link link: me.actualPath){
//						normTreeG.get(link.getSrc()).remove(link);
//						normTreeG.get(link.getDst()).remove(link);
//					}
					treeCost = treeCost - me.metricCost;
					for(int i:gs.keySet()){
						if(gs.get(i) != null && gs.get(i).equals(me)){
							gs.put(i, null);
//							gs.remove(i);
							break;
						}
					}
					//TODO How gs changed
					break;
				case 2: 
					MetricEdge upEdge = meList.get(0);
					Long upNode = upEdge.dest;
					MetricEdge downEdge = meList.get(1);
					Long downNode = downEdge.dest;
					//remove and add new tunnels
					metricTreeG.remove(switchID);
					metricTreeG.get(upNode).remove(upEdge);
					metricTreeG.get(downNode).remove(downEdge);
					MetricEdge newEdge = getPath(upNode, downNode);
					MetricEdge newEdgeSym = newEdge.getSymmetricEdge();
					metricTreeG.get(upNode).add(newEdge);
					metricTreeG.get(downNode).add(newEdgeSym);
					treeCost = treeCost + newEdge.metricCost
										- upEdge.metricCost
										- downEdge.metricCost;
					for(int i:gs.keySet()){
						if(gs.get(i).equals(upEdge) || gs.get(i).equals(downEdge)){
							gs.remove(i);
							break;
						}
					}
					gs.put(round, newEdge);
					break;
				default: 
					Collections.sort(meList);
					MetricEdge oldToNewEdge = meList.get(meList.size()-1);
					Long newcenter = oldToNewEdge.dest; //last one with smallest dist to the oldremoved node
					//add and remove edgeds in metricTreeG
					metricTreeG.remove(switchID);
					
					metricTreeG.get(newcenter).remove(oldToNewEdge.getSymmetricEdge());
					for(int i=0;i<meList.size()-1;i++){
						MetricEdge edge = meList.get(i);
						Long node = edge.dest;
						metricTreeG.get(node).remove(edge.getSymmetricEdge());
						ArrayList<Link> pathToNewCenter = new ArrayList<Link>();
						pathToNewCenter.addAll(edge.actualPath);
						pathToNewCenter.addAll(oldToNewEdge.actualPath);
						MetricEdge edgeToNewCenter = new MetricEdge(node, newcenter,
								pathToNewCenter,edge.metricCost + oldToNewEdge.metricCost);
						metricTreeG.get(node).add(edgeToNewCenter);
						metricTreeG.get(newcenter).add(edgeToNewCenter.getSymmetricEdge());
						for(int j:gs.keySet()){
							if(gs.get(j).equals(edge)){
								gs.put(j, edgeToNewCenter);
								break;
							}
						}
					}
					break;
			}
			
		}
		
		private MetricEdge getPath(Long src, Long dst){
			BroadcastTree tree = destinationRootedTrees.get(dst);
			if(src == dst){
				return null;
			}
			ArrayList<Link> path = new ArrayList<Link>();
			int cost = 0;
			Long tmpsrc = src;
			do{
				Link link = tree.getTreeLink(tmpsrc);
				if(link == null) break;
				cost += tree.getCost(src);
				tmpsrc = link.getDst();
				path.add(link);
			}while(true);
			MetricEdge metricEdge = new MetricEdge(src, dst, path, cost );
			return metricEdge;
		}
		
	}

}
