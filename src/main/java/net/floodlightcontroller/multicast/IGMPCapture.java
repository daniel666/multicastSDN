package net.floodlightcontroller.multicast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IGMP;
import net.floodlightcontroller.packet.IGMP.Record;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.TopologyInstanceForST;
import net.floodlightcontroller.util.OFMessageDamper;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class IGMPCapture implements IOFMessageListener, IFloodlightModule{
    protected static Logger log = LoggerFactory.getLogger(IGMPCapture.class);
	protected static byte IGMP_PROTOCOL_TYPE = 2;
//	public static int MULTICAST_APP_ID = 111;
    protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms
    protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; // TODO: find sweet spot

//    private static final long appCookie = AppCookie.makeCookie(
//    											MULTICAST_APP_ID, 0);
	private static final short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5;
	private static final short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0;
//    static {
//        AppCookie.registerApp(MULTICAST_APP_ID, "Multicast");
//    }
	protected IFloodlightProviderService floodlightProvider;
	protected IMulticastService stEngine;
    protected ICounterStoreService counterStore;
    protected OFMessageDamper messageDamper;

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return IGMPCapture.class.getSimpleName();

	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l =
		        new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    log = LoggerFactory.getLogger(IGMPCapture.class);
	    stEngine = context.getServiceImpl(IMulticastService.class);
        this.messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                EnumSet.of(OFType.FLOW_MOD),
                OFMESSAGE_DAMPER_TIMEOUT);
        this.counterStore = context.getServiceImpl(ICounterStoreService.class);
	    
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
	    floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		switch (msg.getType()) {
	        case PACKET_IN:
			try {
				return processPacketInMessage(sw, (OFPacketIn) msg, cntx);
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//	        case FLOW_REMOVED:
//	            return processFlowRemovedMessage(sw, (OFFlowRemoved) msg);
	        case ERROR:
	            log.info("received an error {} from switch {}", msg, sw);
	            return Command.CONTINUE;
	        default:
	            break;
		}
	    log.error("received an unexpected message {} from switch {}", msg, sw);
	    return Command.CONTINUE;
	}

	private synchronized net.floodlightcontroller.core.IListener.Command processPacketInMessage(
			IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) 
					throws CloneNotSupportedException {
		// TODO Auto-generated method stub
	        // Read in packet data headers by using OFMatch
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
        							IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        
        if (eth.getPayload() instanceof IPv4 == false){
        	return Command.CONTINUE;
        }
        IPv4 ipPkt = (IPv4) eth.getPayload();
        int sourceAddr = ipPkt.getSourceAddress();
        
        if(ipPkt.getPayload() instanceof IGMP == false){
        	return Command.CONTINUE;
        }
        IGMP igmpPkt = (IGMP) ipPkt.getPayload();
       
        
        if(igmpPkt.getNumOfRecord()!=0){
        	for(Record record: igmpPkt.getRecords()){
        		HashMap<Long, ArrayList<Link>> switchAddLinks
					= new HashMap<Long, ArrayList<Link>>();
        		HashMap<Long, ArrayList<Link>> switchRemoveLinks
					= new HashMap<Long, ArrayList<Link>>();
        		 ArrayList<Link> addLinks;
        			ArrayList<Link> removeLinks;
        		int multicastAddr = record.getMulticastAddress();
        		
        		switch(record.getRecordType()){
        		case Record.CHANGE_TO_EXCLUDE_MODE:
        			if(stEngine.getTerminals().contains(sw.getId()))
        			{
        				break;
        			}
//        			log.info(">>>Capture IGMP Join Message: src:{}, mul:{}",  
//        					new Object[]{	
//        							IPv4.fromIPv4Address(sourceAddr), 
//        							IPv4.fromIPv4Address(multicastAddr)
//        					}
//        			);
//        			try {
//						Thread.sleep(100);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
        			stEngine.updateST(sw.getId(),
        							TopologyInstanceForST.ADD);
        			//for debug
        			stEngine.printSTTopo();
        		
//
//        			addLinks = stEngine.getAddLinks();
//        			removeLinks = stEngine.getRemoveLinks();
//        			if(addLinks == null || removeLinks == null){
//        				break;
//        			}
//        			switchAddLinks = convertToMap(addLinks);
//        			switchRemoveLinks = convertToMap(removeLinks);
//        			pushFlowMod(sw.getId(), pi, match, switchAddLinks, switchRemoveLinks, cntx);
//        			
        			
//        			pushPacket(sw, pi, switchLinks.get(sw.getId()), cntx );
        			break;
        		case Record.CHANGE_TO_INCLUDE_MODE:
        			if(stEngine.getTerminals().contains(sw.getId()) == false)
        			{
        				break; 
        			}
//        			log.info("Capture IGMP Leave Message: src:{}, mul:{}", 
//        					new Object[]{	
//								IPv4.fromIPv4Address(sourceAddr), 
//								IPv4.fromIPv4Address(multicastAddr)
//							}
//        			);
        			stEngine.updateST(sw.getId(),
							TopologyInstanceForST.REMOVE);
        			//for debug
        			stEngine.printSTTopo();
//
//        			addLinks = stEngine.getAddLinks();
//        			removeLinks = stEngine.getRemoveLinks();
//        			if(addLinks == null || removeLinks == null){
//        				break;
//        			}
//        			for(Link addlink: addLinks){
//        				Long node1 = addlink.getSrc();
//        				Long node2 = addlink.getDst();
//        				if(switchAddLinks.containsKey(node1) == false){
//        					switchAddLinks.put(node1, new ArrayList<Link>());
//        				}
//        				switchAddLinks.get(node1).add(addlink);
//        				if(switchAddLinks.containsKey(node2) == false){
//        					switchAddLinks.put(node2, new ArrayList<Link>());
//        				}
//        				switchAddLinks.get(node2).add(addlink);
//        			}
//        			pushFlowMod(sw.getId(), pi, match, switchAddLinks, switchRemoveLinks, cntx);

        			break;
        		}
        	}
        }
        return Command.CONTINUE;
	}

	private HashMap<Long, ArrayList<Link>> convertToMap(ArrayList<Link> linksList) {
		// TODO Auto-generated method stub
		HashMap<Long, ArrayList<Link>> retMap = 
								new HashMap<Long, ArrayList<Link>>();
		for(Link addlink: linksList){
			Long node1 = addlink.getSrc();
			Long node2 = addlink.getDst();
			if(retMap.containsKey(node1) == false){
				retMap.put(node1, new ArrayList<Link>());
			}
			retMap.get(node1).add(addlink);
			if(retMap.containsKey(node2) == false){
				retMap.put(node2, new ArrayList<Link>());
			}
			retMap.get(node2).add(addlink);
		}
		return retMap;
	}

	private void pushPacket(IOFSwitch sw, OFPacketIn pi,
			ArrayList<Link> outLinks, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		if (pi == null) {
            return;
        }
		
		if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} pi={}",
                      new Object[] {sw, pi});
        }

        OFPacketOut po =
                (OFPacketOut) floodlightProvider.getOFMessageFactory()
                                                .getMessage(OFType.PACKET_OUT);

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        for(Link link: outLinks){
        	Short outport = link.getOtherPort(sw.getId());
        	OFActionOutput action = new OFActionOutput(outport, (short) 0xffff);
            actions.add(action);
        }
        po.setActions(actions)
        	.setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH *
                outLinks.size()));
        
        short poLength =
                (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);
        int bufferID = pi.getBufferId();
        if( bufferID!=0 && bufferID != OFPacketOut.BUFFER_ID_NONE)
            po.setBufferId(pi.getBufferId());
        else {
            po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        }
        if (po.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }
        po.setInPort(pi.getInPort());
        po.setLength(poLength);
        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, po);
            messageDamper.write(sw, po, cntx);
        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }

	}

	private void pushFlowMod(Long ingressDPID, OFPacketIn pi,
							OFMatch match,
							HashMap<Long, ArrayList<Link>> switchAddLinks,
							HashMap<Long, ArrayList<Link>> switchRemoveLinks,
							FloodlightContext cntx) {
		// TODO Auto-generated method stub
		OFFlowMod fm =
                (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                              .getMessage(OFType.FLOW_MOD);
//		OFActionOutput action = new OFActionOutput();
//        action.setMaxLength((short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        fm.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
        .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
        .setBufferId(OFPacketOut.BUFFER_ID_NONE)
//        .setCookie(appCookie)
        .setCommand(OFFlowMod.OFPFC_ADD)
        .setMatch(match)
        .setActions(actions);
		 

        //for adding switch rules
		 for(Long switchDPID: switchAddLinks.keySet()){
			 	IOFSwitch sw = floodlightProvider.getSwitch(switchDPID);
	            if (sw == null) {
	                if (log.isWarnEnabled()) {
	                    log.warn("Unable to push Multicast Flow Modification,"
	                    		+ " switch at DPID {} " +
	                            "not available", switchDPID);
	                }
	                return;
	            }
	            ArrayList<Link> links = switchAddLinks.get(switchDPID);
	            try {
					addFlowOnSwitchLinks(sw,
							fm.clone(), match.clone(),
							links, cntx);
				} catch (CloneNotSupportedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		 }
		 
		 
		 //for removing rules
		 int multicastAddr = match.getNetworkDestination();
		 HashMap<Long, HashSet<Link>> linkMap = stEngine.getLinkMap();
		 
		 for(Long switchid: switchRemoveLinks.keySet()){
			 OFMatch removeMatch = new OFMatch();
			 IOFSwitch affectedSwitch = floodlightProvider.getSwitch(switchid);
			 removeMatch.setNetworkDestination(multicastAddr);
			 // send flow_mod delete
			 OFFlowMod flowMod = (OFFlowMod) floodlightProvider.
					 		getOFMessageFactory().getMessage(OFType.FLOW_MOD);
			 removeMatch.setWildcards(~OFMatch.OFPFW_NW_DST_ALL);  //hihgly suspicious 
			 flowMod.setMatch(removeMatch);
			 flowMod.setCommand(OFFlowMod.OFPFC_DELETE);
			 try {
				affectedSwitch.write(flowMod, null);
			 } catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			 }
			 //add back other remaining links
			 ArrayList<Link> remainedLinks = new ArrayList<Link>(linkMap.get(switchid));
			 remainedLinks.removeAll(switchRemoveLinks.get(switchid));
			 try {
				addFlowOnSwitchLinks(affectedSwitch,
						 			fm.clone(), match.clone(),
						 			remainedLinks, cntx);
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		 }
	}
	
	private void addFlowOnSwitchLinks(IOFSwitch sw, 
									  OFFlowMod fm ,
									  OFMatch match,
									  ArrayList<Link> links,
									  FloodlightContext cntx){
        ArrayList<Short> totalPorts = new ArrayList<Short>();
        Long switchDPID = sw.getId();
        for(Link alink: links){
        	totalPorts.add(alink.getOtherPort(switchDPID));
        }
        for(Link alink: links){
           Short inport = alink.getOtherPort(switchDPID);
           ArrayList<Short> outports = new ArrayList<Short>(totalPorts);
           outports.remove(inport);
           addFlowOnSwitchLinkPorts(sw, 
        		   				fm,
        		   				match,
        		   				inport, 
        		   				outports, cntx);
        }
	}
	
	private void addFlowOnSwitchLinkPorts(IOFSwitch sw, OFFlowMod fm ,
									OFMatch match,
									Short inport, 
									ArrayList<Short> outports,
									FloodlightContext cntx){
		 // set the match.
        int multicastAddr = match.getNetworkDestination();
        match.setNetworkDestination(multicastAddr);
        match.setWildcards(Wildcards.FULL.matchOn(Flag.IN_PORT).
        									matchOn(Flag.NW_DST));
        
        fm.setMatch(match);
        for(Short outport: outports){
        	fm.getActions().add(new OFActionOutput().
        								setPort(outport));
        }
        fm.getMatch().setInputPort(inport);
        ((OFPacketOut) fm.getActions()).setActionsLength(
				(short) (OFActionOutput.MINIMUM_LENGTH *
						outports.size()));
		int  length = OFFlowMod.MINIMUM_LENGTH+ 
					((OFPacketOut)fm.getActions()).getActionsLength(); 
		fm.setLengthU(length);
		 try {
             counterStore.updatePktOutFMCounterStoreLocal(sw, fm);
             if (log.isTraceEnabled()) {
                 log.trace("Pushing Multicast flowmod multicast switchDPID={} " +
                         "sw={} inPort={} actions={}",
                         new Object[] {sw.getId(),
                                       sw,
                                       fm.getMatch().getInputPort(),
                                       fm.getActions() });
             }
             messageDamper.write(sw, fm, cntx);
         } catch (IOException e) {
             log.error("Failure writing flow mod", e);
         }
	}

}
