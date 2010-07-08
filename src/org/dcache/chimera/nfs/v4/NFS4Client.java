/*
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
package org.dcache.chimera.nfs.v4;

/**
 *  with great help of William A.(Andy) Adamson
 */
import org.dcache.chimera.nfs.ChimeraNFSException;
import org.dcache.chimera.nfs.v4.xdr.nfsstat4;
import org.dcache.chimera.nfs.v4.xdr.stateid4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class NFS4Client {

    private static final Logger _log = LoggerFactory.getLogger(NFS4Client.class);

    /**
     * Server boot id.
     */
    private final static long BOOTID = (System.currentTimeMillis() / 1000);

    /**
     * client id generator.
     */
    private final static AtomicLong CLIENTID = new AtomicLong(0);

    /*
     * from NFSv4.1 spec:
     *
     *
     *  A server's client record is a 5-tuple:
     *
     *   1. co_ownerid
     *          The client identifier string, from the eia_clientowner structure
     *          of the EXCHANGE_ID4args structure
     *   2. co_verifier:
     *          A client-specific value used to indicate reboots, from
     *          the eia_clientowner structure of the EXCHANGE_ID4args structure
     *   3. principal:
     *         The RPCSEC_GSS principal sent via the RPC headers
     *   4. client ID:
     *          The shorthand client identifier, generated by the server and
     *          returned via the eir_clientid field in the EXCHANGE_ID4resok structure
     *   5. confirmed:
     *          A private field on the server indicating whether or not a client
     *          record has been confirmed. A client record is confirmed if there
     *          has been a successful CREATE_SESSION operation to confirm it.
     *          Otherwise it is unconfirmed. An unconfirmed record is established
     *          by a EXCHANGE_ID call. Any unconfirmed record that is not confirmed
     *          within a lease period may be removed.
     *
     */

    /**
     * The client identifier string, from the eia_clientowner structure
     * of the EXCHANGE_ID4args structure
     */
    private final String _ownerId;

    /**
     * A client-specific value used to indicate reboots, from
     * the eia_clientowner structure of the EXCHANGE_ID4args structure
     */

    private final byte[] _verifier;
    /**
     * The RPCSEC_GSS principal sent via the RPC headers.
     */
    private final String _principal;
    /**
     * Client id generated by the server.
     */
    private final long _clientId;

    /**
     * A flag to indicate whether or not a client record has been confirmed.
     */
    private boolean _isConfirmed = false;

    // per client wide unique counter of client stateid requests
    // generated by client, incremented by server
    // See 8.1.3.1 of draft-10:
    // the server MUST provide an "seqid" value starting at one...
    private int _seqid = 0;
    
    private Map<stateid4, NFS4State> _clinetStates = new HashMap<stateid4, NFS4State>();
    /**
     * sessions associated with the client
     */
    private final List<NFSv41Session> _sessions = new ArrayList<NFSv41Session>();
    private long _cl_time = System.currentTimeMillis();        // time of last lease renewal

    /*

    Client identification is encapsulated in the following structure:

    struct nfs_client_id4 {
        verifier4     verifier;
        opaque        id<NFS4_OPAQUE_LIMIT>;
    };

    The first field, verifier is a client incarnation verifier that is
    used to detect client reboots.  Only if the verifier is different
    from that which the server has previously recorded the client (as
    identified by the second field of the structure, id) does the server
    start the process of canceling the client's leased state.

    The second field, id is a variable length string that uniquely
    defines the client.

     */

    /**
     * Client's {@link InetSocketAddress} seen by server.
     */
    private final InetSocketAddress _clientAddress;
    /**
     * Server's {@link InetSocketAddress} seen by client;
     */
    private final InetSocketAddress _localAddress;
    private ClientCB _cl_cb = null; /* callback info */


    public NFS4Client(InetSocketAddress clientAddress, InetSocketAddress localAddress,
            String ownerID, byte[] verifier, String principal) {

        _ownerId = ownerID;
        _verifier = verifier;
        _principal = principal;
        _clientId = (BOOTID << 32) + CLIENTID.incrementAndGet();

        _clientAddress = clientAddress;
        _localAddress = localAddress;

        _log.debug("New client id: {}", Long.toHexString(_clientId));

    }

    public void setCB(ClientCB cb) {
        _cl_cb = cb;
    }

    public ClientCB getCB() {
        return _cl_cb;
    }

    /**
     *
     * @return owner id
     */
    public String id() {
        return _ownerId;
    }

    /**
     *
     * @return client generated verifier
     */
    public byte[] verifier() {
        return _verifier;
    }

    /**
     *
     * @return client id generated by server
     */
    public long id_srv() {
        return _clientId;
    }

    public boolean verify_verifier(byte[] testVerifier) {
        return Arrays.equals(_verifier, testVerifier);
    }

    public boolean verify_serverId(long serverId) {
        return serverId == _clientId;
    }

    public boolean isConfirmed() {
        return _isConfirmed;
    }

    public void confirmed() {
        confirmed(true);
    }

    public void confirmed(boolean confirmed) {
        _isConfirmed = confirmed;
    }

    public long leaseTime() {
        return _cl_time;
    }

    /**
     * sets client lease time with current time
     * @param max_lease_time
     * @throws ChimeraNFSException if difference between current time and last
     * lease more than max_lease_time
     */
    public void updateLeaseTime(long max_lease_time) throws ChimeraNFSException {

        long curentTime = System.currentTimeMillis();
        if ((curentTime - _cl_time) > max_lease_time * 1000) {
            throw new ChimeraNFSException(nfsstat4.NFS4ERR_EXPIRED, "lease time expired");
        }
        _cl_time = curentTime;
    }

    /**
     * sets client lease time with current time
     */
    public void refreshLeaseTime() {
        _cl_time = System.currentTimeMillis();
    }

    /**
     * Get the client's {@link InetSocketAddress} seen by server.
     * @return client's address
     */
    public InetSocketAddress getRemoteAddress() {
        return _clientAddress;
    }

    /**
     * Get server's {@link InetSocketAddress} seen by the client.
     * @return server's address
     */
    public InetSocketAddress getLocalAddress() {
        return _localAddress;
    }

    public int nextSeqID() {
        return ++_seqid;
    }

    public int currentSeqID() {
        return _seqid;
    }

    public void addState(NFS4State state) {
        _clinetStates.put(state.stateid(), state);
    }

    public NFS4State state(stateid4 stateid) {
        return _clinetStates.get(stateid);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(_clientAddress).append(":").
                append(_ownerId).append("@").append(_clientId);
        return sb.toString();
    }

    public NFSv41Session getSession(int id) {
        if (id >= _sessions.size()) {
            return null;
        }
        return _sessions.get(id);
    }

    /**
     *
     * @return list of sessions created by client.
     */
    public List<NFSv41Session> sessions() {
        return _sessions;
    }

    public void addSession(NFSv41Session session) {
        _sessions.add(session);
    }

    public void removeSession(NFSv41Session session) {
        _sessions.remove(session);
    }

    public boolean sessionsEmpty(NFSv41Session session) {
        return _sessions.isEmpty();
    }

    public int sessionsLeft(NFSv41Session session) {
        return _sessions.size();
    }

    public String principal() {
        return _principal;
    }
}