/*
 * Copyright (c) 2009 - 2012 Deutsches Elektronen-Synchroton,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program (see the file COPYING.LIB for more
 * details); if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.dcache.chimera.nfs.v4.xdr;
import org.dcache.chimera.nfs.nfsstat;
import org.dcache.chimera.nfs.v4.*;
import org.dcache.xdr.*;
import java.io.IOException;

public class GET_DIR_DELEGATION4res implements XdrAble {
    public int gddr_status;
    public GET_DIR_DELEGATION4res_non_fatal gddr_res_non_fatal4;

    public GET_DIR_DELEGATION4res() {
    }

    public GET_DIR_DELEGATION4res(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        xdrDecode(xdr);
    }

    public void xdrEncode(XdrEncodingStream xdr)
           throws OncRpcException, IOException {
        xdr.xdrEncodeInt(gddr_status);
        switch ( gddr_status ) {
        case nfsstat.NFS_OK:
            gddr_res_non_fatal4.xdrEncode(xdr);
            break;
        default:
            break;
        }
    }

    public void xdrDecode(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        gddr_status = xdr.xdrDecodeInt();
        switch ( gddr_status ) {
        case nfsstat.NFS_OK:
            gddr_res_non_fatal4 = new GET_DIR_DELEGATION4res_non_fatal(xdr);
            break;
        default:
            break;
        }
    }

}
// End of GET_DIR_DELEGATION4res.java