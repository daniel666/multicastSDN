package net.floodlightcontroller.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import net.floodlightcontroller.routing.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class SharedTreeInstance {
	HashMap<Long, ArrayList<MetricEdge>> edges;
	int cost;
	Long root;
	int round;
	HashSet<Long> terminals;
	//instrumentation
	HashSet<Link> pLinks;
	int pCost;
	HashSet<Link> oldpLinks;
	int linkdff;
	
	SharedTreeInstance(){
		edges = new HashMap<Long, ArrayList<MetricEdge>>();
		terminals = new HashSet<Long>();
//		pLinks = new HashSet<Link>();
	}
	
    protected static Logger log = LoggerFactory.getLogger(SharedTreeInstance.class);

	public void update(Long switchID, int request) {
		// TODO Auto-generated method stub
		oldpLinks = pLinks == null? new HashSet<Link>(): new HashSet<Link>(pLinks);
		MetricEdge me ;
		MetricEdge meR ; 
		switch(request){
		case TopologyInstanceForST.ADD:
			if(round==0){
				root = switchID;
				break;
			}
			me = TopologyInstanceForST.getPath(switchID, root);
//			meR = TopologyInstanceForST.getPath(root, switchID);
			meR = me.reverse();
			addEdge(me);
			addEdge(meR);
			cost += me.metricCost;
			terminals.add(switchID);
			break;
		case TopologyInstanceForST.REMOVE:
			if(switchID == root)
				break;
			me = TopologyInstanceForST.getPath(switchID, root);
//			meR = TopologyInstanceForST.getPath(root, switchID);
			meR = me.reverse();
			removeEdge(me);
			removeEdge(meR);
			cost -= me.metricCost;
			terminals.remove(switchID);
			break;
		}
		getPhyInstrument();
		round++;
	}
	
	private void getPhyInstrument(){
		pLinks = new HashSet<Link>();
		for(Long switchid: edges.keySet()){
			for(MetricEdge me: edges.get(switchid)){
				for(Link link: me.actualPath){
					pLinks.add(link);
				}
			}
		}
		pCost = 0;
		for(Link link: pLinks){
			pCost += TopologyInstanceForST.linkCost.get(link);
		}
		pCost = pCost / 2; //because link is counted twice in both direction
	
		//link difference number
		linkdff = Sets.symmetricDifference(pLinks, oldpLinks).immutableCopy().size()/2;
	}

	private void removeEdge(MetricEdge me) {
		// TODO Auto-generated method stub
		Long src = me.src;
		edges.get(src).remove(me);
	}

	private void addEdge(MetricEdge me) {
		// TODO Auto-generated method stub
		Long src = me.src;
		
		if(!edges.containsKey(src) || edges.get(src) == null){
			edges.put(src, new ArrayList<MetricEdge>());
		}
		edges.get(src).add(me);
	}

	public void print() {
		// TODO Auto-generated method stub
		log.info("	***Shared Tree topology after round {}:", round - 1);
		log.info("	***Logical Tree Cost: {}", cost);
		log.info("	***terminals: {}", terminals);
		log.info("	***Logical Edges: {}", edges);
		log.info("	***Pysical Links:{}", pLinks);
		log.info("	***Pysical Tree Cost:{}", pCost);
		log.info("  ***Num of different links:{}", linkdff);
		log.info("	*********");
		
	}
	
	
}
