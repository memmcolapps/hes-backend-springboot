package com.memmcol.hes.Utils;

import com.memmcol.hes.service.MMXCRC16;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.DataType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class readWrapperLoginHeart {
    public byte[] readWrapperLoginMMX313 (byte[] data) {
        MMXCRC16 mmxcrc16 = new MMXCRC16();
        int version, client, server, length, code1, code2, meterNoLength, specialCode, crc, calcCRC, calcCRCResponse;
        byte[] pre_meterno, meterno;
        byte[] response = new byte[data.length];
        GXByteBuffer buff = new GXByteBuffer();
        try {
            buff = new GXByteBuffer(data);
            //Check length is correct
            if (buff.size() - buff.position() != 26 && buff.size() - buff.position() != 25) {
                log.error("Wrong data : {}", gurux.dlms.internal.GXCommon.toHex(data, true));
                return response;
            }

            //Heartbeat
            //Decoding the data received.
            if (buff.size() - buff.position() == 25) {
                // Get version
                version = buff.getUInt16();
                //get Server Address
                server = buff.getUInt16();
                //Get Client Address
                client = buff.getUInt16();
                //Get length
                length = buff.getUInt16();
                //Function code 1
                code1 = buff.getUInt8();
                //function code 2
                code2 = buff.getUInt8();
                //METERNO LENGTH
                meterNoLength = buff.getUInt8();
                //meter no
                meterno = buff.subArray(buff.position(), meterNoLength);
                String smeter = "";
                for (byte b : meterno) {
                    smeter = smeter + " ";
                    smeter = smeter + (char) (b & 0xFF);;
                }
                log.info("Heart Meter No:, {}", smeter);
                String strMeterNo = GXDLMSClient.changeType(meterno, DataType.STRING).toString();
                buff.position(buff.position() + meterNoLength);
                //get special code
//                specialCode = buff.getUInt8();
                crc = buff.getUInt16();

                byte[] temp1 = buff.subArray(9, 14);
                calcCRC = mmxcrc16.countFCS16(buff.array(), 9, 14);
                //Confirm calculated CRC = data received CRC
                if (calcCRC == crc) {
                    //
                } else {
                    //
                }

                //Response  to Meter
                //Preparing the response data
                int ss = buff.size();
                GXByteBuffer reply = new GXByteBuffer(buff.size());
                buff.position(0);
                //set version
                reply.setUInt16(version);
                //set client address
                reply.setUInt16(client);
                //set server address
                reply.setUInt16(server);
                //set length
                reply.setUInt16(length);
                //set function code 1 response
                reply.setUInt8(0xCC);
                //set function code 2 response
                reply.setUInt8(0x03);
                //set meterno LENGTH
                reply.setUInt8(meterNoLength);
                //set meterno
                reply.set(meterno);
                //SET special code
//                reply.setUInt8(specialCode);
                //calc CRC
                byte[] temp2 = reply.subArray(9, 14);
                calcCRCResponse = mmxcrc16.countFCS16(reply.array(), 9, 14);
                reply.setUInt16((short) calcCRCResponse);

                response = reply.subArray(0, 25);
               }
        } catch (Exception e){
            log.error(e.getMessage());
            e.printStackTrace();
        }

        return response;
    }
}
