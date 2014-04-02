package de.measite.minidns;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;

import de.measite.minidns.Record.CLASS;
import de.measite.minidns.Record.TYPE;

/**
 * A minimal DNS client for SRV/A/AAAA/NS and CNAME lookups, with IDN support.
 * This circumvents the missing javax.naming package on android.
 */
public class Client {

    /**
     * The internal random class for sequence generation.
     */
    protected Random random;

    /**
     * Create a new DNS client.
     */
    public Client() {
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e1) {
            random = new SecureRandom();
        }
    }

    /**
     * Query a nameserver for a single entry.
     * @param name The DNS name to request.
     * @param type The DNS type to request (SRV, A, AAAA, ...).
     * @param clazz The class of the request (usually IN for Internet).
     * @param host The DNS server host.
     * @return 
     * @throws IOException On IO Errors.
     */
    public DNSMessage query(String name, TYPE type, CLASS clazz, String host)
        throws IOException
    {
        Question q = new Question();
        q.setClazz(clazz);
        q.setType(type);
        q.setName(name);
        return query(q, host);
    }

    /**
     * Query the system nameserver for a single entry.
     * @param name The DNS name to request.
     * @param type The DNS type to request (SRV, A, AAAA, ...).
     * @param clazz The class of the request (usually IN for Internet).
     * @return The DNSMessage reply or null.
     */
    public DNSMessage query(String name, TYPE type, CLASS clazz)
    {
        Question q = new Question();
        q.setClazz(clazz);
        q.setType(type);
        q.setName(name);
        return query(q);
    }

    /**
     * Query a specific server for one entry.
     * @param q The question section of the DNS query.
     * @param host The dns server host.
     * @throws IOException On IOErrors.
     */
    public DNSMessage query(Question q, String host) throws IOException {
        DNSMessage message = new DNSMessage();
        message.setQuestions(new Question[]{q});
        message.setRecursionDesired(true);
        message.setId(random.nextInt());
        byte[] buf = message.toArray();
        DatagramSocket socket = new DatagramSocket();
        DatagramPacket packet = new DatagramPacket(
                buf, buf.length, InetAddress.getByName(host), 53);
        socket.setSoTimeout(5000);
        socket.send(packet);
        packet = new DatagramPacket(new byte[513], 513);
        socket.receive(packet);
        DNSMessage dnsMessage = DNSMessage.parse(packet.getData());
        if (dnsMessage.getId() != message.getId()) {
            return null;
        }
        return dnsMessage;
    }

    /**
     * Query the system DNS server for one entry.
     * @param q The question section of the DNS query.
     */
    public DNSMessage query(Question q) {
        String dnsServer[] = findDNS();
        for (String dns : dnsServer) {
            try {
                DNSMessage message = query(q, dns);
                if (message == null) {
                    continue;
                }
                if (message.getResponseCode() !=
                    DNSMessage.RESPONSE_CODE.NO_ERROR) {
                    continue;
                }
                for (Record record: message.getAnswers()) {
                    if (record.isAnswer(q)) {
                        return message;
                    }
                }
            } catch (IOException ioe) {
            }
        }
        return null;
    }

    /**
     * Retrieve a list of currently configured DNS servers.
     * @return The server array.
     */
    public String[] findDNS() {
        try {
            Process process = Runtime.getRuntime().exec("getprop");
            InputStream inputStream = process.getInputStream();
            LineNumberReader lnr = new LineNumberReader(
                new InputStreamReader(inputStream));
            String line = null;
            HashSet<String> server = new HashSet<String>(6);
            while ((line = lnr.readLine()) != null) {
                int split = line.indexOf("]: [");
                if (split == -1) {
                    continue;
                }
                String property = line.substring(1, split);
                String value = line.substring(split + 4, line.length() - 1);
                if (property.endsWith(".dns") || property.endsWith(".dns1") ||
                    property.endsWith(".dns2") || property.endsWith(".dns3") ||
		    property.endsWith(".dns4")) {
                    server.add(value);
                }
            }
            if (server.size() > 0) {
                return server.toArray(new String[server.size()]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
