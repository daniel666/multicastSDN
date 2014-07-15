package net.floodlightcontroller.multicast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IGMP;
import net.floodlightcontroller.packet.IGMP.Record;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IGMPCapture implements IOFMessageListener, IFloodlightModule{
    protected static Logger log = LoggerFactory.getLogger(IGMPCapture.class);
	protected static byte IGMP_PROTOCOL_TYPE = 2;
	
	protected IFloodlightProviderService floodlightProvider;

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
	            return processPacketInMessage(sw, (OFPacketIn) msg, cntx);
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

	private net.floodlightcontroller.core.IListener.Command processPacketInMessage(
			IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		// TODO Auto-generated method stub
	        // Read in packet data headers by using OFMatch

        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                                              IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        if (!eth.isMulticast()) {
        	return Command.CONTINUE;
        }
        IPv4 pkt = (IPv4) eth.getPayload();
        int sourceAddr = pkt.getSourceAddress();
        IGMP igmpPkt = (IGMP) pkt.getPayload();
        
        
        if(igmpPkt.getNumOfRecord()!=0){
        	for(Record record: igmpPkt.getRecords()){
        		int multicastAddr = record.getMulticastAddress();
        		switch(record.getRecordType()){
        		case Record.CHANGE_TO_EXCLUDE_MODE:
        			log.info("Capture IGMP Join Message: src:{}, mul:{}",  
        					new Object[]{	
        							IPv4.fromIPv4Address(sourceAddr), 
        							IPv4.fromIPv4Address(multicastAddr)
        					}
        			);
        			break;
        		case Record.CHANGE_TO_INCLUDE_MODE:
        			log.info("Capture IGMP Leave Message: src:{}, mul:{}", 
        					new Object[]{	
								IPv4.fromIPv4Address(sourceAddr), 
								IPv4.fromIPv4Address(multicastAddr)
							}
        			);
        			break;
        		}
        	}
        }
	        OFMatch match = new OFMatch();
	        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
	        Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
	        Long destMac = Ethernet.toLong(match.getDataLayerDestination());
	        byte netprotocol = match.getNetworkProtocol();
	        if( netprotocol != IGMP_PROTOCOL_TYPE)
	        	return Command.CONTINUE;
	        return Command.CONTINUE;
	}

}
