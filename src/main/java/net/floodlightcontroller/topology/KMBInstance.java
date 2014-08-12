package net.floodlightcontroller.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Link;

public class KMBInstance {
	Map<Long, BroadcastTree> destinationRootedTrees;
    protected static Logger log = LoggerFactory.getLogger(KMBInstance.class);
    
	private class Graph{
		HashMap<Long, ArrayList<Long>> neighbourmap;
		
		public Graph(){
			neighbourmap = new HashMap<Long, ArrayList<Long>>();
		}
		
		public Graph(Graph g){
			neighbourmap = new HashMap<Long, ArrayList<Long>>(g.neighbourmap);
		}
		
		public void addedge(Long src, Long dst) {
			// TODO Auto-generated method stub
			if(neighbourmap.get(src) == null){
				neighbourmap.put(src, new ArrayList<Long>());
			}
			
			//don't add duplicate edge
			if(neighbourmap.get(src).contains(dst))
				return;
			neighbourmap.get(src).add(dst);
		}
		
		private void removeLeave(Long leave){
			if(leave==null)
				return;
			Long upnode = neighbourmap.get(leave).get(0);
			neighbourmap.remove(leave);
			neighbourmap.get(upnode).remove(leave);
			log.info("KMB removing leaves:{} and its upnode:{}" , leave, upnode);
		}
		
		@Override
		public String toString(){
			return neighbourmap.toString();
		}
	}
	protected class NodeDist implements Comparable<NodeDist> {
        private final Long node;
        public Long getNode() {
            return node;
        }

        private final int dist;
        public int getDist() {
            return dist;
        }

        public NodeDist(Long node, int dist) {
            this.node = node;
            this.dist = dist;
        }

        @Override
        public int compareTo(NodeDist o) {
            if (o.dist == this.dist) {
                return (int)(this.node - o.node);
            }
            return this.dist - o.dist;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            NodeDist other = (NodeDist) obj;
            if (node == null) {
                if (other.node != null)
                    return false;
            } else if (!node.equals(other.node))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            assert false : "hashCode not designed";
            return 42;
        }

    }
	Graph metricGraph;
	Graph metricTree;
	Graph phyGraph;
	Graph phyTree;
	int pCost;
	
	//instrument
	Graph oldphyTree;
	int diffpLinks;
	Set<Link> linkdiff;
	HashSet<Long> myterm;
	
//	public KMBInstance(Set<Long> terminals){
//			metricGraph = new Graph();
//			for(Long switchid: terminals){
//				ArrayList<Long> neighbours = new ArrayList<Long>();
//				for(Long terminal: terminals){
//					if(switchid.equals(terminal))
//						continue;
//					neighbours.add(terminal);
//				}
//				metricGraph.neighbourmap.put(switchid, neighbours);
//			}
//	}
	
	public KMBInstance() {
		// TODO Auto-generated constructor stub
		destinationRootedTrees = TopologyInstanceForST.destinationRootedTrees;
	}

	private void setup(HashSet<Long> terminals){
		myterm = new HashSet<Long>(terminals);
		oldphyTree = phyTree == null? new Graph() : new Graph(phyTree);//copy
		metricGraph = new Graph();
		for(Long switchid: terminals){
			ArrayList<Long> neighbours = new ArrayList<Long>();
			for(Long terminal: terminals){
				if(switchid.equals(terminal))
					continue;
				neighbours.add(terminal);
			}
			metricGraph.neighbourmap.put(switchid, neighbours);
		}
		
		linkdiff = null;
	}
	public void calculate(HashSet<Long> terminals) {
		// TODO Auto-generated method stub
		setup(terminals); //refresh every member
		
		metricTree = MST(metricGraph);
		phyGraph = m2p(metricTree);
		phyTree = pMST(phyGraph);
		rmLeaves();
		getPhyInstrument();
	}

	/*
	 * Utilities Functions
	 */
	void print(){
		log.info("		xxxxxxxxx KMP Instance physical edge: {}", phyTree);
		log.info("		xxxxxxxxx KMP Instance pCost: {}", pCost);
		log.info("      xxxxxxxxx KMP Instance num of link change: {}", diffpLinks);
		log.info("______________________KMB link diff:{}", linkdiff);
		log.info("		xxxxxxxxxxxxxxxxxxx");
	}
	
	private ArrayList<Long> getNTLeave(){
		ArrayList<Long> ret = new ArrayList<Long>();
		if(phyTree == null)
			return null;
		if(myterm == null)
			return null;
		
		for(Long switchid: phyTree.neighbourmap.keySet()){
			if(myterm.contains(switchid))
				continue;
			if(phyTree.neighbourmap.get(switchid).size()==1){
				ret.add(switchid);
			}
		}
		
		if(ret.isEmpty())
			return null;
		return ret;
	}
	private void rmLeaves(){
		if(phyTree == null)
			return;
		if(myterm == null)
			return;
		do{
			ArrayList<Long> leaves = getNTLeave();
			if(leaves == null)
				break;
			log.info("KMB spot non terminal leaves" , leaves);
			for(Long leave: leaves){
				phyTree.removeLeave(leave);
			}
		}while(true);
		
		
	}
	private void getPhyInstrument() {
		// TODO Auto-generated method stub
		pCost = 0;
		for(Long src: phyTree.neighbourmap.keySet()){
				pCost += phyTree.neighbourmap.get(src).size();
		}
		pCost = pCost/2;
		
		HashSet<Link> linkset = new HashSet<Link>();
		HashSet<Link> oldlinkset = new HashSet<Link>();
		for(Long src: phyTree.neighbourmap.keySet()){
				for(Long dst:phyTree.neighbourmap.get(src)){
					linkset.add(new Link(src, 0, dst, 0 ));
				}
		}
		
		for(Long src: oldphyTree.neighbourmap.keySet()){
			for(Long dst:oldphyTree.neighbourmap.get(src)){
				oldlinkset.add(new Link(src, 0, dst, 0 ));
			}
		}
		log.info("KMB plink set:{}", linkset);
		log.info("KMB old plink set:{}", oldlinkset);
		linkdiff = Sets.symmetricDifference(linkset, oldlinkset).immutableCopy();
		diffpLinks = linkdiff.size() / 2;

	}

	/*
	 * Core Functions
	 */
	private Graph m2p(Graph graph) {
		// TODO Auto-generated method stub
		Graph retgraph = new Graph();
//		Map<Long, BroadcastTree> drtree = getDRTree(graph);
		for(Long src: graph.neighbourmap.keySet()){
			for(Long dst: graph.neighbourmap.get(src)){
				
				//note subtlety here path between src-dst and dst maynot be same
				for(Link link: getPath(src, dst, destinationRootedTrees)){
					retgraph.addedge(link.getSrc(), link.getDst());
//					retgraph.addedge(dst, src);
				}
			}
		}
		return retgraph;
	}

	private ArrayList<Link> getPath(Long src, Long dst,  Map<Long, BroadcastTree> drtree) {
		// TODO Auto-generated method stub
		ArrayList<Link> retpath = new ArrayList<Link>();
		BroadcastTree btree = drtree.get(dst);
		Link link = btree.getTreeLink(src) ;
		while( link != null){
			retpath.add(link);
			src = link.getDst();
			link = btree.getTreeLink(src) ;
		}
		return retpath;
	}
	
	
	
	private Map<Long, BroadcastTree> getDRTree(Graph graph){
		Map<Long, BroadcastTree> drtree = new HashMap<Long, BroadcastTree> ();
		for(Long switchid: graph.neighbourmap.keySet()){
			//build BroadcastTree rooted at switchid
			HashMap<Long, Integer> cost = new HashMap<Long, Integer>();
			HashMap<Long, Link> pred = new HashMap<Long, Link>();
			
			HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
	        PriorityQueue<NodeDist> explore = new PriorityQueue<NodeDist>();
	        
			for(Long tmp: graph.neighbourmap.keySet()){
				cost.put(tmp, Integer.MAX_VALUE);
				pred.put(tmp, null);
			}

			explore.add(new NodeDist(switchid, 0));
	        cost.put(switchid, 0);
	        while(explore.peek() != null){
	        	NodeDist chosen = explore.poll();
	        	Long node = chosen.getNode();
	        	int dist = chosen.getDist();
	            if (seen.containsKey(node)) continue;
	        	seen.put(node, true);
	        	for(Long neighbour: graph.neighbourmap.get(node)){
	        		if(seen.containsKey(neighbour))
	        			continue;
	        		int tmp = dist + 1;
	        		if(tmp < cost.get(neighbour)){
	        			cost.put(neighbour, tmp);
	        			pred.put(neighbour, new Link(neighbour, 0, node, 0));
	        		}
	        		NodeDist nd = new NodeDist(neighbour, tmp);
	        		explore.remove(nd);
	        		explore.add( nd );
	        	}
	        }
	        
	        BroadcastTree btree = new BroadcastTree(pred, cost);
	        drtree.put(switchid, btree);
		}
		return drtree;
	}
	
	
	private Graph MST(Graph graph){
		return MST__Core(graph, destinationRootedTrees);
	}
	
	private Graph pMST(Graph graph){
		Map<Long, BroadcastTree> drtree = getDRTree(graph);
		return MST__Core(graph, drtree);
	}
	//Prim's MST algorithm
	private Graph MST__Core(Graph graph, Map<Long, BroadcastTree> drtree) {
		// TODO Auto-generated method stub
		Graph retgraph = new Graph();
		ArrayList<Long> treeNodes = new ArrayList<Long>();
		ArrayList<Long> nonTreeNodes = new ArrayList<Long>(graph.neighbourmap.keySet());
		if(nonTreeNodes.isEmpty() == false)
			treeNodes.add(nonTreeNodes.remove(0));
		
		while(nonTreeNodes.isEmpty() == false){
			int min = Integer.MAX_VALUE;
			Long chosenTreeNode = null;
			Long chosenNonTreeNode = null;
			
			for(Long treenode: treeNodes){
				BroadcastTree btree = drtree.get(treenode);
				for(Long nontreenode: nonTreeNodes){
					if(btree.getCost(nontreenode) < min){
						min = btree.getCost(nontreenode);
						chosenTreeNode = treenode;
						chosenNonTreeNode = nontreenode;
					}
				}
			}
			treeNodes.add(chosenNonTreeNode);
			nonTreeNodes.remove(chosenNonTreeNode);
			retgraph.addedge(chosenTreeNode, chosenNonTreeNode);
			retgraph.addedge(chosenNonTreeNode, chosenTreeNode);
		}
		return retgraph;
	}

}
