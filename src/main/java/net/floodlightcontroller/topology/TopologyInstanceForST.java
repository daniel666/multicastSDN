package net.floodlightcontroller.topology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.KMBInstance.NodeDist;

import org.python.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TopologyInstanceForST{
//	protected Map<Integer, SteinerTree> sterinTrees;
    protected static Logger log = LoggerFactory.getLogger(TopologyInstanceForST.class);

	protected static Map<Long, BroadcastTree> destinationRootedTrees ;
	protected Map<Long, Map<Long, Integer>> metricSpaceDist;

	static Map<Link, Integer> linkCost = new HashMap<Link, Integer>(); //cost functions for physical topology link
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
//		Map<Long, Set<Short>> switchPorts = nt.switchPorts; //ports facing switches not hosts
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
                	metric.put(switchid, tree.getCost(switchid));
                }
                metricSpaceDist.put(node, metric);
            }
		}
		//other attributes of a ST instance
		steinerTree = new SteinerTree();
		oldsteinerTree = new SteinerTree();
//		int tunnel_weight = switchPorts.size() + 1;
		int tunnel_weight = 1;
		log.info("tunnel_wight:{}", tunnel_weight);
		treeOptCost = new ArrayList<Integer>();  
	    for(NodePortTuple npt: switchPortLinks.keySet()) {
	        if (switchPortLinks.get(npt) == null) continue;
	        for(Link link: switchPortLinks.get(npt)) {
	            if (link == null) continue;
	            linkCost.put(link, tunnel_weight);
	        }
	    }
	}
	public HashMap<Long, HashSet<Link>> getLinkMap(){
		return steinerTree.getLinkMap();
	}
	
	public void updateST(Long switchID, int request){
		switch(request){
		case ADD:
			log.info(">>>Round {}: Capture IGMP Add request from {}", round, switchID);
			oldsteinerTree = new SteinerTree(steinerTree);
			steinerTree.preInstrumentation();
			steinerTree.add(switchID);
			
			steinerTree.getPhyInstrument();
			break;
		case REMOVE:
			log.info(">>>Round {}: Capture IGMP Remove request from {}", round, switchID);
			oldsteinerTree = new SteinerTree(steinerTree);
			steinerTree.preInstrumentation();
			steinerTree.remove(switchID);
			steinerTree.getPhyInstrument();

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
		log.info("---Steiner Tree topology after round {}:", round - 1);
		log.info("---Metric Tree Cost: {}", steinerTree.treeCost);
		log.info("---Tree Optimal Cost:{}", treeOptCost);
		log.info("---terminals: {}", steinerTree.terminals);
		log.info("---Metric Edges: {}", steinerTree.metricTreeG);
		log.info("---Tracking edge data structure gs: {}", steinerTree.gs);
		log.info("---Greedy Edge:{}", steinerTree.greedyEdge);
		log.info("---Swap #:{}, Swapped Edges:{}",steinerTree.swapEdges.size(), steinerTree.swapEdges);
		log.info("---Removed Edges due to remove request:{}", steinerTree.removeEdges);
		log.info("---pLinks:{}", steinerTree.pLinks);
		log.info("---pCost:{}", steinerTree.pCost);
		log.info("----------");

	}
	class SteinerTree {
		protected Map<Long, ArrayList<MetricEdge>> metricTreeG; //tree edges
		protected Set<Long> terminals; //current terminals
		protected int treeCost; //currentTree cost in metric space
		protected HashMap<Integer, MetricEdge> gs;
		protected int l;  //first round that is larger than epsilon currentOPT
		
		/*
		 *
		 *For Instrumentation Purpose
		 * 
		 */
		protected HashMap<MetricEdge, MetricEdge> swapEdges;
		protected MetricEdge greedyEdge;
		protected ArrayList<MetricEdge> removeEdges;
		//physcial links and cost
		protected HashSet<Link>	pLinks;
		protected int pCost;


		public SteinerTree(){
			metricTreeG = new HashMap<Long, ArrayList<MetricEdge>>();
			gs = new HashMap<Integer, MetricEdge>();
			terminals = new HashSet<Long>();
			swapEdges = new HashMap<MetricEdge, MetricEdge>();
			greedyEdge = null;
			removeEdges = new ArrayList<MetricEdge>();
			
			pLinks = new HashSet<Link>();
			
		}
		
		public SteinerTree(SteinerTree st){
			metricTreeG = new HashMap<Long, ArrayList<MetricEdge>>(st.metricTreeG);
			treeCost = st.treeCost;
			gs = new HashMap<Integer, MetricEdge>(st.gs);
			terminals = new HashSet<Long>(st.terminals);
			l = st.l;
			swapEdges = new HashMap<MetricEdge, MetricEdge>(st.swapEdges);
			greedyEdge = new MetricEdge(st.greedyEdge); 
			removeEdges = new ArrayList<MetricEdge>(st.removeEdges);
			
			pLinks = new HashSet<Link>(st.pLinks);
			
		}
		
		private void preInstrumentation(){
			swapEdges = new HashMap<MetricEdge, MetricEdge>();
			greedyEdge = null; 
			removeEdges = new ArrayList<MetricEdge>();
			
			//physical instrumentation
			pLinks = new HashSet<Link>();
			pCost = 0;
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
			HashSet<Link> st = new HashSet();
			if(steinerTree == null || steinerTree.metricTreeG == null || steinerTree.metricTreeG.size() == 0){
				return null;
			}
			for(Long node: steinerTree.metricTreeG.keySet()){
				for(MetricEdge edge:steinerTree.metricTreeG.get(node)){
					st.addAll(edge.actualPath);
				}
			}
			HashSet<Link> oldst = new HashSet<Link>();
			for(Long node: oldsteinerTree.metricTreeG.keySet()){
				for(MetricEdge edge:oldsteinerTree.metricTreeG.get(node)){
					oldst.addAll(edge.actualPath);
				}
			}
			st.removeAll(oldst);
			return Lists.newArrayList(st);
		}
		
		/*
		 * Utilities 
		 * 
		 */
		private void addMetricEdge(Long switchID, MetricEdge me){
			if(metricTreeG.get(switchID) == null)
				metricTreeG.put(switchID, new ArrayList<MetricEdge>());
			metricTreeG.get(switchID).add(me);
		}
		
		private void getPhyInstrument(){
			pLinks = new HashSet<Link>();
			
			for(Long switchid: metricTreeG.keySet()){
				for(MetricEdge me: metricTreeG.get(switchid)){
					for(Link link: me.actualPath){
						pLinks.add(link);
					}
				}
			}
			
			pCost = 0;
			for(Link link: pLinks){
				pCost += linkCost.get(link);
			}
			pCost = pCost/2;
		}
		
		/*
		 * 
		 * Core Functions
		 */
		private void doSwap(MetricEdge removeEdge, MetricEdge replaceEdge){
			metricTreeG.get(removeEdge.src).remove(removeEdge);
			metricTreeG.get(removeEdge.dest).remove(removeEdge.reverse());
			
			addMetricEdge(replaceEdge.src, replaceEdge);
			addMetricEdge(replaceEdge.dest, replaceEdge.reverse());
//			metricTreeG.get(replaceEdge.src).add(replaceEdge);
//			metricTreeG.get(replaceEdge.dest).add(replaceEdge.reverse());
			log.info("	SWAP: Remove Edge {} with {}", removeEdge,  replaceEdge);
			
			//for instrumentation purpose
			swapEdges.put(removeEdge, replaceEdge);
			for(int i: gs.keySet()){
				if(gs.get(i) == null)
					continue;
				if(gs.get(i).equals(removeEdge) || gs.get(i).equals(removeEdge.reverse())){
					log.info("	replacing gs[{}] with {}", i,  replaceEdge);
					gs.put(i, replaceEdge);
				}
			}
			treeCost = treeCost - removeEdge.metricCost + replaceEdge.metricCost;
			log.info("	Adjust currentTreeCost to {}", treeCost);
		}
		
		private MetricEdge getSwapEdge(MetricEdge removeEdge){
			MetricEdge replaceEdge = null;
			
			ArrayList<Long> leftTree = new ArrayList<Long>();
			ArrayList<Long> rightTree = new ArrayList<Long>();

			Long src = removeEdge.src;
			Long dest= removeEdge.dest;
			log.info("	Edge remove candidate {}", removeEdge );
			double threshold2 = removeEdge.metricCost * 1.0/(1+epsilon);
			log.info("	Swappable edges r3: cost is smaller than： {}", threshold2 );

			LinkedList<Long> toexplore = new LinkedList<Long>();
			HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
			toexplore.add(src);
			seen.put(dest, true);
			while(toexplore.size()!= 0){
				Long switchid = toexplore.removeFirst();

				if(seen.containsKey(switchid) == true) continue;
				leftTree.add(switchid);
				seen.put(switchid, true);
				ArrayList<MetricEdge> metricEdges = metricTreeG.get(switchid);
				for(MetricEdge me: metricEdges){
					if(seen.containsKey(me.dest) == false)
						toexplore.add(me.dest);
				}
			}
			
			toexplore.clear();
			seen.clear();
			toexplore.add(dest);
			seen.put(src,  true);
			while(toexplore.size()!= 0){
				Long switchid = toexplore.removeFirst();
				if(seen.containsKey(switchid) == true) continue;
				seen.put(switchid, true);
				rightTree.add(switchid);
				ArrayList<MetricEdge> metricEdges = metricTreeG.get(switchid);
				for(MetricEdge me: metricEdges){
					if(seen.containsKey(me.dest) == false)
						toexplore.add(me.dest);
				}
			}
			
			log.info("		Left Tree Dividide by the edge {} ", leftTree);
			log.info("		Right Tree Dividide by the edge {} ", rightTree);

			for(Long leftnode: leftTree){
				for(Long rightnode: rightTree){
					if(metricSpaceDist.get(leftnode).get(rightnode) < threshold2){
						replaceEdge = getPath(leftnode, rightnode);
					}
				}
			}
			
			return replaceEdge;
		}
		public void add(Long switchID) {
			// TODO Auto-generated method stub
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
			log.info("---Add Started");
			
			HashMap<Long, Integer> metric = (HashMap<Long, Integer>) metricSpaceDist.get(switchID);
//			terminals.add(switchID);
			
			//find shortest path to the tree from new terminal, note removed node is removed from terminal
			int shortestMetricDist = Integer.MAX_VALUE;
			Long other=(long) 0;
			for(Long terminal: terminals){
				int distance = metric.get(terminal);
				if(distance < shortestMetricDist){
					shortestMetricDist = distance;
					other = terminal;
//					gs.put(round, getPath(terminal, switchID));
				}
			}
			treeCost = treeCost + shortestMetricDist;
			MetricEdge edge = getPath(other, switchID);
//			MetricEdge edge2 = getPath(switchID, other);
			MetricEdge edge2 = edge.reverse();
			addMetricEdge(other, edge);
			addMetricEdge(switchID, edge2);
			gs.put(round, edge);
			log.info("add shortest path: {}",  edge);
			//for instrumentation
			greedyEdge = new MetricEdge(edge);
			
			//get lt[round]
			l=-1;
			for(int i=round-1;i>=0;i--){
				if (treeCost*epsilon > treeOptCost.get(i)){
					l = i + 1;
//					lt.add(l);
					break;
				};
			}
			//get removable edges
			double threshold;
			threshold = round==l? Double.NaN: epsilon * treeOptCost.get(round-1)/(round-l);
			log.info("removable edges r1: from round {} to {}", l, round-1);
			log.info("removable edges r2: cost is larger than： {}", threshold);
//			ArrayList<MetricEdge> removableEdges  = new ArrayList<MetricEdge>();
			PriorityQueue<MetricEdge> rmcandidates = new PriorityQueue<MetricEdge>();

			for(int i=l; i<= round-1; i++){
				if(gs.get(i) == null)
					continue; //this is an edge that has been deleted
				if(gs.get(i).metricCost > threshold)
//					removableEdges.add(gs.get(i));
					rmcandidates.add(gs.get(i));
			}
//			Collections.sort(removableEdges);
			
			
			//get edges that can replace the removable edges
			log.info("removable edges candidates of r1 and r2: {}", rmcandidates);
			while(true){
				if(rmcandidates.peek()==null)
					break;
				MetricEdge removeEdge = rmcandidates.poll();
				MetricEdge swapinEdge = getSwapEdge(removeEdge);
				if(swapinEdge == null)
					break;
				doSwap(removeEdge, swapinEdge);
				rmcandidates.add(swapinEdge);
			}
//----------------------------------------------------------------
//			for(MetricEdge removeEdge: removableEdges){
//				ArrayList<Long> leftTree = new ArrayList<Long>();
//				ArrayList<Long> rightTree = new ArrayList<Long>();
//
//				Long src = removeEdge.src;
//				Long dest= removeEdge.dest;
//				log.info("	Edge remove candidate {}", removeEdge );
//				double threshold2 = removeEdge.metricCost * 1.0/(1+epsilon);
//				log.info("	Swappable edges r3: cost is smaller than： {}", threshold2 );
//
//				LinkedList<Long> toexplore = new LinkedList<Long>();
//				HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
//				toexplore.add(src);
//				seen.put(dest, true);
//				int counter=0;
//				while(toexplore.size()!= 0){
//					Long switchid = toexplore.removeFirst();
//
//					if(seen.containsKey(switchid) == true) continue;
//					leftTree.add(switchid);
//					seen.put(switchid, true);
//					ArrayList<MetricEdge> metricEdges = metricTreeG.get(switchid);
//					for(MetricEdge me: metricEdges){
//						toexplore.add(me.dest);
//					}
//				}
//				
//				toexplore.clear();
//				seen.clear();
//				toexplore.add(dest);
//				seen.put(src,  true);
//				while(toexplore.size()!= 0){
//					Long switchid = toexplore.removeFirst();
//					if(seen.containsKey(switchid) == true) continue;
//					seen.put(switchid, true);
//					rightTree.add(switchid);
//					ArrayList<MetricEdge> metricEdges = metricTreeG.get(switchid);
//					for(MetricEdge me: metricEdges){
//						toexplore.add(me.dest);
//					}
//				}
//				
//				log.info("		Left Tree Dividide by the edge {} ", leftTree);
//				log.info("		Right Tree Dividide by the edge {} ", rightTree);
//
//				for(Long leftnode: leftTree){
//					for(Long rightnode: rightTree){
//						if(metricSpaceDist.get(leftnode).get(rightnode) < threshold2){
//							metricTreeG.get(removeEdge.src).remove(removeEdge);
////							metricTreeG.get(removeEdge.dest).remove(removeEdge);
//							metricTreeG.get(removeEdge.dest).remove(removeEdge.reverse());
//
//							MetricEdge addEdge = getPath(leftnode,rightnode);
////							MetricEdge addEdge2 = getPath(rightnode,leftnode);
//							MetricEdge addEdge2 = addEdge.reverse();
//							log.info("	SWAP: Remove Edge {} with {}", removeEdge,  addEdge);
//							
//							//for instrumentation purpose
//							swapEdges.put(removeEdge, addEdge);
//							
//							metricTreeG.get(leftnode).add(addEdge);
//							metricTreeG.get(rightnode).add(addEdge2);
//							for(int i: gs.keySet()){
//								if(gs.get(i) == null)
//									continue;
//								if(gs.get(i).equals(removeEdge) || gs.get(i).equals(removeEdge.reverse())){
//									log.info("	replacing gs[{}] with {}", i,  addEdge);
//									gs.put(i, addEdge);
//								}
//							}
//							treeCost = treeCost - removeEdge.metricCost + addEdge.metricCost;
//							log.info("	Adjust currentTreeCost to {}", treeCost);
//						}
//					}
//				}
//			}
			
			
			terminals.add(switchID);
			log.info("---Add Ended");

		}
		
		public void remove(Long switchID) {
			// TODO Auto-generated method stub
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
					//for instrumentation
					removeEdges.add(me);
					
//					metricTreeG.get(me.dest).remove(me); //see if directly delete is okay
					metricTreeG.get(me.dest).remove(me.reverse()); //
					log.info("Removing edge:{}", me);
					metricTreeG.remove(switchID);
					treeCost = treeCost - me.metricCost;
					for(int i:gs.keySet()){
						if (gs.get(i) == null)
								continue;
						if(gs.get(i).equals(me) || gs.get(i).equals(me.reverse())){
							gs.put(i, null);
							break;
						}
					}
					//TODO How gs changed
					break;
				case 2: 
					MetricEdge upEdge = meList.get(0);
					Long upNode = upEdge.dest;
					MetricEdge downEdge = meList.get(1);
					
					//for instrumentation
					removeEdges.add(upEdge);
					removeEdges.add(downEdge);
					
					Long downNode = downEdge.dest;
					//remove and add new tunnels
					metricTreeG.remove(switchID);
					log.info("removing edge:{} and {}", upEdge, downEdge);
//					metricTreeG.get(upNode).remove(upEdge);
//					metricTreeG.get(downNode).remove(downEdge);
					metricTreeG.get(upNode).remove(upEdge.reverse());
					metricTreeG.get(downNode).remove(downEdge.reverse());
					MetricEdge newEdge = getPath(upNode, downNode);
//					MetricEdge newEdgeSym = getPath(downNode, upNode);
					MetricEdge newEdgeSym = newEdge.reverse();
					log.info("adding edge:{} and {}", newEdge, newEdgeSym);
					
					addMetricEdge(upNode, newEdge);
					addMetricEdge(downNode, newEdgeSym);
					
//					metricTreeG.get(upNode).add(newEdge);
//					metricTreeG.get(downNode).add(newEdgeSym);
					treeCost = treeCost + newEdge.metricCost
										- upEdge.metricCost
										- downEdge.metricCost;
					for(int i:gs.keySet()){
						if(gs.get(i) == null)
							continue;
						if(gs.get(i).equals(upEdge) || gs.get(i).equals(upEdge.reverse())){
							gs.put(i, null);
						}
						else if(gs.get(i).equals(downEdge) || gs.get(i).equals(downEdge.reverse())){
							gs.put(i, null);
						}
					}
					gs.put(round, newEdge);
					break;
				default: 
					log.info("Degree of {} is {}, no need to remove", 
							new Object[]{
								switchID,
								meList.size()
					});
					gs.put(round, null);
//					Collections.sort(meList);
//					MetricEdge oldToNewEdge = meList.get(meList.size()-1);
//					Long newcenter = oldToNewEdge.dest; //last one with smallest dist to the oldremoved node
//					//add and remove edgeds in metricTreeG
//					metricTreeG.remove(switchID);
//					
//					metricTreeG.get(newcenter).remove(oldToNewEdge.getSymmetricEdge());
//					for(int i=0;i<meList.size()-1;i++){
//						MetricEdge edge = meList.get(i);
//						Long node = edge.dest;
//						metricTreeG.get(node).remove(edge.getSymmetricEdge());
//						ArrayList<Link> pathToNewCenter = new ArrayList<Link>();
//						pathToNewCenter.addAll(edge.actualPath);
//						pathToNewCenter.addAll(oldToNewEdge.actualPath);
//						MetricEdge edgeToNewCenter = new MetricEdge(node, newcenter,
//								pathToNewCenter,edge.metricCost + oldToNewEdge.metricCost);
//						metricTreeG.get(node).add(edgeToNewCenter);
//						metricTreeG.get(newcenter).add(edgeToNewCenter.getSymmetricEdge());
//						for(int j:gs.keySet()){
//							if(gs.get(j).equals(edge)){
//								gs.put(j, edgeToNewCenter);
//								break;
//							}
//						}
//					}
					break;
			}
			
		}
	}
	
	protected static MetricEdge getPath(Long src, Long dst){
		BroadcastTree tree = destinationRootedTrees.get(dst);
		if(src == dst){
			return null;
		}
		ArrayList<Link> path = new ArrayList<Link>();
		int cost = tree.getCost(src);
		Long tmpsrc = src;
		do{
			Link link = tree.getTreeLink(tmpsrc);
			if(link == null) break;
//			cost += tree.getCost(src);
			tmpsrc = link.getDst();
			path.add(link);
		}while(true);
		MetricEdge metricEdge = new MetricEdge(src, dst, path, cost );
		return metricEdge;
	}

}
