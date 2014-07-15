package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;


//0                   1                   2                   3
//0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|  Type = 0x22  |    Reserved   |           Checksum            |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|           Reserved            |  Number of Group Records (M)  |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|                                                               |
//.                                                               .
//.                        Group Record [1]                       .
//.                                                               .
//|                                                               |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|                                                               |
//.                                                               .
//.                        Group Record [2]                       .
//.                                                               .
//|                                                               |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|                               .                               |
//.                               .                               .
//|                               .                               |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|                                                               |
//.                                                               .
//.                        Group Record [M]                       .
//.                                                               .
//|                                                               |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+


//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|  Record Type  |  Aux Data Len |     Number of Sources (N)     |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|                       Multicast Address                       |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|                       Source Address [1]                      |
//+-                                                             -+
//|                       Source Address [2]                      |
//+-                                                             -+
//.                               .                               .
//.                               .                               .
//.                               .                               .
//+-                                                             -+
//|                       Source Address [N]                      |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

public class IGMP extends BasePacket {
	static byte IGMP_TYPE = 0x22;
	
	byte type;
	short checksum;
	short numOfRecord;
	Record[] records;
	
	public byte getType() {
		return type;
	}

	public void setType(byte type) {
		this.type = type;
	}

	public short getChecksum() {
		return checksum;
	}

	public void setChecksum(short checksum) {
		this.checksum = checksum;
	}

	public short getNumOfRecord() {
		return numOfRecord;
	}

	public void setNumOfRecord(short numOfRecord) {
		this.numOfRecord = numOfRecord;
	}

	public Record[] getRecords() {
		return records;
	}

	public void setRecords(Record[] records) {
		this.records = records;
	}

	public class Record{
		public static final short MODE_IS_INCLUDE = 0x1;
		public static final short MODE_IS_EXCLUDE = 0x2;
		public static final short CHANGE_TO_INCLUDE_MODE = 0x3;
		public static final short CHANGE_TO_EXCLUDE_MODE = 0x4;
		
		byte recordType;
		byte auxDataLen;
		short numOfSources;
		int multicastAddress;
		int[] soureceAddress;
		
		public short getRecordType() {
			return recordType;
		}
		public void setRecordType(byte recordType) {
			this.recordType = recordType;
		}
		public short getAuxDataLen() {
			return auxDataLen;
		}
		public void setAuxDataLen(byte auxDataLen) {
			this.auxDataLen = auxDataLen;
		}
		public short getNumOfSources() {
			return numOfSources;
		}
		public void setNumOfSources(short numOfSources) {
			this.numOfSources = numOfSources;
		}
		public int getMulticastAddress() {
			return multicastAddress;
		}
		public void setMulticastAddress(int multicastAddress) {
			this.multicastAddress = multicastAddress;
		}
		public int[] getSoureceAddress() {
			return soureceAddress;
		}
		public void setSoureceAddress(int[] soureceAddress) {
			this.soureceAddress = soureceAddress;
		}
		
	}
	@Override
	public byte[] serialize() {
		// TODO Auto-generated method stub
		int recordlength=0;
		for(int i=0; i<numOfRecord; i++){
			Record record = records[i];
			recordlength+=8+record.soureceAddress.length*4;
		}
		int length = 8 + recordlength;

		byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.put(type);
        bb.put((byte) 0);//reserved
        bb.putShort(checksum);
        bb.put((byte) 0);//reserved
        bb.putShort(numOfRecord);
        for(int i=0; i<numOfRecord; i++){
        	Record record = records[i];
        	bb.put(record.recordType);
        	bb.put(record.auxDataLen);
        	bb.putShort(record.numOfSources);
        	bb.putInt(record.multicastAddress);
        	for(int j=0; j<record.soureceAddress.length;j++)
        		bb.putInt(record.soureceAddress[j]);
        }
        // compute checksum if needed
//        if (this.checksum == 0) {
//            bb.rewind();
//            int accumulation = 0;
//            for (int i = 0; i < this.headerLength * 2; ++i) {
//                accumulation += 0xffff & bb.getShort();
//            }
//            accumulation = ((accumulation >> 16) & 0xffff)
//                    + (accumulation & 0xffff);
//            this.checksum = (short) (~accumulation & 0xffff);
//            bb.putShort(10, this.checksum);
//        }
        return data;
	}

	@Override
	public IPacket deserialize(byte[] data, int offset, int length)
			throws PacketParsingException {
		// TODO Auto-generated method stub
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        
        this.type = bb.get();
        bb.get();
        this.checksum = bb.getShort();
        bb.getShort();
        this.numOfRecord = bb.getShort();
        if (this.type != IGMP_TYPE) {
            throw new PacketParsingException(
                    "Invalid type for IGMP packet: " +
                    this.type);
        }
        this.records= new Record[numOfRecord]; 
        for(int i=0; i<this.numOfRecord; i++){
        	Record record = new Record();
        	record.recordType = bb.get();
        	record.auxDataLen = bb.get();
        	record.numOfSources = bb.getShort();
        	record.multicastAddress = bb.getInt();
        	for(int j=0; j< record.numOfSources; j++){
            	record.soureceAddress[j] = bb.getInt();
        	}
        	this.records[i] = record;
        }
		return this;
	}

}
