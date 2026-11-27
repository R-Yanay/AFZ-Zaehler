package ch.afzlab.simulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.random.RandomGenerator;

import ch.afzlab.common.trdp.DoorStatus;
import ch.afzlab.common.trdp.PdTelegram;

/**
 * Sendet den Tuerstatus zyklisch als TRDP-aehnliches PD-Telegramm per UDP.
 * Ohne Verbindungsaufbau und ohne Bestaetigung: Geht ein Telegramm verloren, kommt im naechsten Zyklus das naechste.
 */
public class DoorStatusSender implements AutoCloseable {

    private final DatagramSocket socket;
    private final InetSocketAddress target;
    private final double dropRate;
    private final RandomGenerator random;
    private long sequence;
    private long sent;
    private long dropped;

    public DoorStatusSender(String host, int port, double dropRate, RandomGenerator random) throws SocketException {
        this.socket = new DatagramSocket();
        this.target = new InetSocketAddress(host, port);
        this.dropRate = dropRate;
        this.random = random;
    }

    public void send(DoorStatus status) throws IOException {
        byte[] bytes = new PdTelegram(sequence, DoorStatus.COM_ID, status.encode()).encode();
        sequence = (sequence + 1) & 0xFFFF_FFFFL;
        // Simulierter Paketverlust: Nummer wird trotzdem verbraucht, der Empfaenger sieht eine Luecke
        if (dropRate > 0 && random.nextDouble() < dropRate) {
            dropped++;
            return;
        }
        socket.send(new DatagramPacket(bytes, bytes.length, target));
        sent++;
    }

    public long sent() {
        return sent;
    }

    public long dropped() {
        return dropped;
    }

    @Override
    public void close() {
        socket.close();
    }
}
