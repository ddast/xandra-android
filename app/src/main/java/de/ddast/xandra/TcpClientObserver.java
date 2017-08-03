package de.ddast.xandra;

/**
 * Created by dast on 8/2/17.
 */

interface TcpClientObserver {
    void connectionEstablished();
    void connectionLost();
}
