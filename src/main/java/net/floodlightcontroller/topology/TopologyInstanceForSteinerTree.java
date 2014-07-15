package net.floodlightcontroller.topology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Link;

public class TopologyInstanceForSteinerTree extends TopologyInstance{
//	protected Map<Integer, SteinerTree> sterinTrees;
	Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
	protected SteinerTree steinerTree;

	public final static int ADD = 1;
	public final static int REMOVE=2;
	public final static double epsilon =  0.5;
	protected int round=0;
	protected ArrayList<Integer> treeOptCost;
	protected ArrayList<Integer> lt;
	
	TopologyInstanceForSteinerTree(){
		steinerTree = new SteinerTree();
		int tunnel_weight = switchPorts.size() + 1;
		treeOptCost = new ArrayList<Integer>();
	    for(NodePortTuple npt: tunnelPorts) {
	        if (switchPortLinks.get(npt) == null) continue;
	        for(Link link: switchPortLinks.get(npt)) {
	            if (link == null) continue;
	            linkCost.put(link, tunnel_weight);
	        }
	    }
	    lt = new ArrayList<Integer>();
	}
	
	TopologyInstanceForSteinerTree(Map<Link, Integer> linkCost){
		this.steinerTree = new SteinerTree();
		treeOptCost = new ArrayList<Integer>();
		this.linkCost = linkCost;
	    lt = new ArrayList<Integer>();

	}
	
	public void update(Long switchID, int request){
		switch(request){
		case ADD:
			steinerTree.add(switchID);
			break;
		case REMOVE:
			steinerTree.remove(switchID);
			break;
		}
		round++;
	}
	
	
	private class SteinerTree {
		private class MetricEdge implements Comparable<MetricEdge>{
			Long src;
			Long dest;
			ArrayList<Link> acttualPath;
			int metricCost;
			public MetricEdge(Long src, Long dest, ArrayList<Link> acttualPath,
					int metricCost) {
				super();
				this.src = src;
				this.dest = dest;
				this.acttualPath = acttualPath;
				this.metricCost = metricCost;
			}
			@Override
			public int compareTo(MetricEdge other) {
				// TODO Auto-generated method stub
				if(src == other.src && dest == other.dest){
					return 0;
				}
				if(src == other.dest && dest == other.src){
					return 0;
				}
				return -metricCost + other.metricCost;
			}
			
		}
		
		protected Map<Long, ArrayList<MetricEdge>> switchToLinks; //tree edges
		protected int treeCost; //currentTree cost
//		protected HashMap<Integer, List<List<Link>>> g; //gsi
		protected HashMap<Integer, MetricEdge> gs;
		protected int l;
		protected Set<Long> terminals; //current terminals
		
		public SteinerTree(){
			switchToLinks = new HashMap<Long, ArrayList<MetricEdge>>();
//			g = new HashMap<Integer, List<List<Link>>>();
			gs = new HashMap<Integer, MetricEdge>();
			terminals = new HashSet<Long>();
		}
		
		public void add(Long switchID) {
			// TODO Auto-generated method stub
			HashMap<Long, Integer> metric = (HashMap<Long, Integer>) metricSpaceDist.get(switchID);
			switchToLinks.put(switchID, new ArrayList<MetricEdge>());
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
			switchToLinks.get(other).add(getPath(other, switchID));
			switchToLinks.get(switchID).add(getPath(switchID, other));
			
			//temporarily set current tree cost
			treeCost = treeCost + shortestMetricDist;
			if(round == 0){
				treeOptCost.add(0);
			}
			else{
				int tmpOptCost = treeCost> treeOptCost.get(round-1)?
						treeCost:
						treeOptCost.get(round-1);
				treeOptCost.add(tmpOptCost);	
			}
			
			//get lt[round]
			for(int i=round;i>=0;i--){
				if (treeCost*epsilon < treeOptCost.get(i)){
					l = i + 1;
					lt.add(l);
					break;
				};
			}
			
			//get removable edges
			ArrayList<MetricEdge> removableEdges  = new ArrayList<MetricEdge>();
			for(int i=l+1; i<= round; i++){
				if(gs.get(i).metricCost > epsilon * treeOptCost.get(round)/(round-l))
					removableEdges.add(gs.get(i));
			}
			Collections.sort(removableEdges);
			
			//get edges that can replace the removalbe edges
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
					ArrayList<MetricEdge> metricEdges = switchToLinks.get(switchid);
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
					ArrayList<MetricEdge> metricEdges = switchToLinks.get(switchid);
					for(MetricEdge me: metricEdges){
						toexplore.add(me.dest);
						rightTree.add(me.dest);
					}
				}
				
				for(Long leftnode: leftTree){
					for(Long rightnode: rightTree){
						if(metricSpaceDist.get(leftnode).get(rightnode)
								*(1+epsilon) < removeEdge.metricCost){
							switchToLinks.get(removeEdge.src).remove(removeEdge);
							switchToLinks.get(removeEdge.dest).remove(removeEdge);
							
							switchToLinks.get(leftnode).add(getPath(leftnode,rightnode));
							switchToLinks.get(rightnode).add(getPath(rightnode, leftnode));
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
			toExtensionTree(switchID);
			
		}
		
		private void toExtensionTree(Long switchID){
			ArrayList<MetricEdge> meList =  switchToLinks.get(switchID);
			switch(meList.size()){
				case 1:
					MetricEdge me = meList.get(0);
					switchToLinks.get(me.dest).
									remove(getPath(me.dest, me.src)); //see if directly delete is okay
					switchToLinks.get(switchID)
									.remove(me);
					terminals.remove(switchID);
					treeCost = treeCost - me.metricCost;
					//TODO How gs changed
					break;
				case 2: 
					Long upNode = meList.get(0).dest;
					MetricEdge upSeenEdge = meList.get(0);
					Long downNode = meList.get(1).dest;
					MetricEdge downSeenEdge = meList.get(1);
					while(switchToLinks.get(upNode).size()==2){
						if(switchToLinks.get(upNode).get(0)
								.compareTo(upSeenEdge) == 0){
							upNode = switchToLinks.get(upNode).get(1).dest;
							upSeenEdge = switchToLinks.get(upNode).get(1);
						}
						else{
							upNode = switchToLinks.get(upNode).get(0).dest;
							upSeenEdge = switchToLinks.get(upNode).get(0);
						}
					}
					
					while(switchToLinks.get(downNode).size()==2){
						if(switchToLinks.get(downNode).get(0)
								.compareTo(downSeenEdge) == 0){
							downSeenEdge = switchToLinks.get(downNode).get(1);
							downNode = downSeenEdge.dest;
						}else{
							downSeenEdge = switchToLinks.get(downNode).get(0);
							downNode = downSeenEdge.dest;
						}
					}
					break;
				default: break;
			}
			
		}
		
		private MetricEdge getPath(Long src, Long dst){
			BroadcastTree tree = destinationRootedTrees.get(dst);
			if(src == dst){
				return null;
			}
			ArrayList<Link> path = new ArrayList<Link>();
			int cost = 0;
			do{
				Link link = tree.getTreeLink(src);
				if(link == null) break;
				cost += tree.getCost(src);
				Long neighbour = link.getDst();
				path.add(link);
			}while(true);
			MetricEdge metricEdge = new MetricEdge(src, dst, path, cost );
			return metricEdge;
		}
		
//		private ArrayList<Link> getShoretestPathToTree(
//					Long newswitch, BroadcastTree tree){
//			int shortestDist = Integer.MAX_VALUE;
//			ArrayList<Link> result = null;
//			for(Long terminal: terminals){
//				Link link = tree.getTreeLink(newswitch);
//				if(link == null){
//					return null;
//				}
//				int dist = 0;
//				Long dstNeighbour;
//				do{
//					dstNeighbour =  link.getDst();
//					dist += linkCost.get(link);
//				}while(dstNeighbour!=newswitch);
//				if(dist<=shortestDist){
//					ArrayList<Link> potentialPath = new ArrayList<Link>();
//					Link alink = tree.getTreeLink(newswitch);
//					Long neighbour;
//					do{
//						neighbour =  link.getDst();
//						potentialPath.add(alink);
//					}while(neighbour!=newswitch);
//					result = potentialPath;
//				}
//			}
//			return result;
//		}
		
		
	}
}
