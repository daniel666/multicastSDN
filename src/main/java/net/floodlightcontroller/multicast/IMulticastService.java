package net.floodlightcontroller.multicast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Link;

public interface IMulticastService extends IFloodlightService {
	//update Steiner Tree 
	public void updateST(Long switchID, int request);
	
	//get links to be added as a result of update
	public ArrayList<Link> getAddLinks(); 
	
	//get links to be removed as a result of update
	public ArrayList<Link> getRemoveLinks();
	
	//get physical links from metric edge representation
	public HashMap<Long, HashSet<Link>> getLinkMap();
	
	public Set<Long> getTerminals();

	public void printSTTopo();
}
