package net.floodlightcontroller.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SteinerTree {
	protected Map<Long, List<Link>> switchToLinks;
	protected int cost;
	protected List<List<Link>> g;
	protected Set<Long> terminals;
	
	public SteinerTree(){
		switchToLinks = new HashMap<Long, List<Link>>();
		g = new ArrayList<List<Link>>();
		terminals = new HashSet<Long>();
	}
	
	public void add(Long switchID) {
		// TODO Auto-generated method stub
		
	}
	
	public void remove(Long switchID) {
		// TODO Auto-generated method stub
		
	}

	
	
}
