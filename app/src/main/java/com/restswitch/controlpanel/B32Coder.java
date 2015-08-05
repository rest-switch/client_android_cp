//
// Copyright 2015 The REST Switch Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, Licensor provides the Work (and each Contributor provides its 
// Contributions) on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied, including, 
// without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR 
// PURPOSE. You are solely responsible for determining the appropriateness of using or redistributing the Work and assume any 
// risks associated with Your exercise of permissions under this License.
//
// Author: John Clark (johnc@restswitch.com)
//

package com.restswitch.controlpanel;


public class B32Coder
{
    ////////////////////////////////////////////////////////////
    public static String encode(final byte[] inbuf)
    {        
        final byte[] enc32 = "0abcdefghjkmnpqrstuvwxyz12346789".getBytes();  // John Clark charset
        //final byte[] enc32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".getBytes();  // RFC-4648 charset

        final int inbuflen = inbuf.length;
        final int bitc = (inbuflen * 8);
        final int whol = (bitc / 5);
        final int otot = (whol + ((0!=(bitc % 5)) ? 1 : 0));

        byte[] outbuf = new byte[otot];

        byte sft = -8;
        byte src = 0;
        for(int i=0, o=otot-1; o >= 0; --o)
        {
            byte bits = (byte)(0x1f & ((sft < 0) ? (((int)src & 0xff) >> (-sft)) : (src << sft)));
            if((sft < -3) && (i < inbuflen))
            {
                // wrapping cases: [4-7:0] [5-7:0-1] [6-7:0-2] [7:0-3] (and 8)
                src = inbuf[i++];
                sft += 8;
                bits |= (byte)(0x1f & ((sft < 0) ? (((int)src & 0xff) >> (-sft)) : (src << sft)));
            }

            // store bits
            outbuf[o] = enc32[0x1f & bits];
            sft -= 5;
        }

        return(new String(outbuf));
    }


    ////////////////////////////////////////////////////////////
    public static byte[] decode(final String encoded)
    {
        final int btot = (encoded.length() * 5);
        final int otot = (btot / 8);

        final byte[] inbuf = encoded.getBytes();
        byte[] outbuf = new byte[otot];

        int sft = -5;
        byte src = 0;
        for(int i=inbuf.length, o=0; o < otot; ++o)
        {
            byte bits = (byte)((sft < 0) ? (((int)src & 0xff) >> (-sft)) : (src << sft));
            while((sft < 3) && (i > 0))
            {
                src = decodeByte(inbuf[--i]);
                sft += 5;
                bits |= (byte)((sft < 0) ? (((int)src & 0xff) >> (-sft)) : (src << sft));
            }

            // store bits
            outbuf[o] = bits;
            sft += -8;
        }

        return(outbuf);
    }

    ////////////////////////////////////////////////////////////
    private static byte decodeByte(final byte bval)
    {
        if((bval >= 'A') && (bval <= 'H')) return((byte)(bval - 'A' + 1));
        if((bval >= 'a') && (bval <= 'h')) return((byte)(bval - 'a' + 1));

        if((bval == 'J') || (bval == 'K')) return((byte)(bval - 'J' + 9));
        if((bval == 'j') || (bval == 'k')) return((byte)(bval - 'j' + 9));

        if((bval == 'M') || (bval == 'N')) return((byte)(bval - 'M' + 11));
        if((bval == 'm') || (bval == 'n')) return((byte)(bval - 'm' + 11));

        if((bval >= 'P') && (bval <= 'Z')) return((byte)(bval - 'P' + 13));
        if((bval >= 'p') && (bval <= 'z')) return((byte)(bval - 'p' + 13));

        if((bval == '0') || (bval == 'O') || (bval == 'o')) return(0);
        if((bval == '1') || (bval == 'I') || (bval == 'i') || (bval == 'L') || (bval == 'l')) return(24);
        if((bval >= '2') && (bval <= '4')) return((byte)(bval - '2' + 25));
        if (bval == '5') return(16); // 5=s
        if((bval >= '6') && (bval <= '9')) return((byte)(bval - '6' + 28));

        return((byte)0xff);
    }


    ////////////////////////////////////////////////////////////
    public static String encodeDatetime(final long datetimems)
    {
        byte[] batmp = new byte[8];
        batmp[0] = (byte)datetimems;
        batmp[1] = (byte)(datetimems >>> 8);
        batmp[2] = (byte)(datetimems >>> 16);
        batmp[3] = (byte)(datetimems >>> 24);
        batmp[4] = (byte)(datetimems >>> 32);
        batmp[5] = (byte)(datetimems >>> 40);
        batmp[6] = (byte)(datetimems >>> 48);
        batmp[7] = (byte)(datetimems >>> 56);

        // encode and trim leading zeros
        String encoded = encode(batmp).replaceAll("^0+", "");
        return(encoded);
    }


    ////////////////////////////////////////////////////////////
    public static String encodeDatetimeNow(int offsetms)
    {
        long utcNow = System.currentTimeMillis();
        String encoded = encodeDatetime(utcNow + (long)offsetms);
        return(encoded);
    }


    ////////////////////////////////////////////////////////////
    public static long decodeDatetime(final String encoded)
    {
        final int enclen = encoded.length();
        if(enclen > 12)
        {
            return(-1); // error, too large
        }

        // pad with leading zeros to make 12 chars
        // agw84rek6 -> 000agw84rek6
        StringBuilder buf = new StringBuilder(12);
        final int pad = (12 - enclen);
        for(int i=0; i<pad; ++i) buf.append('0');
        buf.append(encoded);

        // decode string
        byte[] batmp = decode(buf.toString());

        long datetime = 0;
        for(int i=0; i<batmp.length; ++i)
        {
           datetime += (((long)batmp[i] & 0xffL) << (8 * i));
        }

        return(datetime);
    }
}
