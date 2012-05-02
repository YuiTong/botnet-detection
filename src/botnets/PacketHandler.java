package botnets;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;

import jpcap.PacketReceiver;
import jpcap.packet.Packet;
import jpcap.packet.TCPPacket;
import jpcap.packet.UDPPacket;

class PacketHandler implements PacketReceiver {
	
	private String localNetwork;
	public ArrayList<Host> hosts;
	private ArrayList<Channel> channels;
	
	PacketHandler(String localNetwork){
		this.localNetwork = localNetwork;
		this.hosts = new ArrayList<Host>();
	}
	
	//this method is called every time Jpcap captures a packet
	public void receivePacket(Packet packet) {	
		//compare non local network with blacklist and note matches
		if(packet.header[23] == 17) { //udp packets (IP Packet protocol 17)
			UDPPacket p = (UDPPacket) packet;
			if(p.dst_port == 53) { //dns outbound queries only
				//System.out.println(convert(p.data)); //print data of dns query
			}
		}
		else if(packet.header[23] == 6) { //tcp packets (IP Packet protocol 6)
			TCPPacket p = (TCPPacket) packet;
			storeWorkWeight(p);
			checkForIRC(p);
			String data = new String(p.data);
			if(data.toLowerCase().startsWith("get") || data.toLowerCase().startsWith("post")) {
				storeHttpRequest(p);
			}
				
		}
	}
	
	private void storeHttpRequest(TCPPacket packet) {
		Host current = new Host(packet.src_ip.getHostAddress());
		int index = hosts.indexOf(current); //host will already exist because of storeWorkWeight call
		hosts.get(index).httpRequests.add(new String(packet.data) + "Time: " + System.currentTimeMillis());
	}

	private boolean isLocalNetwork(InetAddress ip) {
		return ip.getHostAddress().startsWith(localNetwork);
	}
	
	private void storeWorkWeight(TCPPacket packet) { //Described in paper here: http://web.cecs.pdx.edu/~jrb/jrb.papers/sruti06/sruti06.pdf
		if(isLocalNetwork(packet.src_ip)) {
			Host current = new Host(packet.src_ip.getHostAddress());
			int index = hosts.indexOf(current);
			if(index != -1) {
				if(packet.ack && packet.syn)
					hosts.get(index).addSynAck();
				else if(packet.syn)
					hosts.get(index).addSyn();
				if(packet.fin)
					hosts.get(index).addFin();
				hosts.get(index).addToTotalSent();
			}
			else {
				current.setSynAck((packet.ack && packet.syn) ? 1 : 0);
				current.setFin((packet.fin) ? 1 : 0);
				current.setRst(0);
				current.setSyn((packet.syn && !packet.ack) ? 1 : 0);
				current.setTotalSent(1);
				hosts.add(current);
			}
		}
		else {
			Host current = new Host(packet.dst_ip.getHostAddress());
			int index = hosts.indexOf(current);
			if(index != -1) {
				if(packet.rst)
					hosts.get(index).addRst();
				if(packet.fin)
					hosts.get(index).addFin();
				hosts.get(index).addToTotalReceived();
			}
			else {
				current.setSynAck(0);
				current.setFin((packet.fin) ? 1 : 0);
				current.setRst((packet.rst) ? 1 : 0);
				current.setSyn(0);
				current.setTotalReceived(1);
				hosts.add(current);
			}
		}
	}
	
	private void checkForIRC(TCPPacket p) {
		String data = new String(p.data).toLowerCase();
		if(data.startsWith("join")) {
			Channel current = new Channel(data.substring(5), p.dst_ip.getHostAddress() + p.dst_port);
			int index = channels.indexOf(current);
			if(index != -1) {
				channels.get(index).addJoin();
			}
			else {
				channels.add(current);
			}
		}
		else if(data.startsWith("ping")) {
			Channel current = new Channel(null, p.dst_ip.getHostAddress() + p.dst_port);
			int index = channels.indexOf(current);
			if(index != -1) {
				channels.get(index).addPing();
			}
		}
		else if(data.startsWith("pong")) {
			Channel current = new Channel(null, p.dst_ip.getHostAddress() + p.dst_port);
			int index = channels.indexOf(current);
			if(index != -1) {
				channels.get(index).addPong();
			}
		}
		else if(data.startsWith("privmsg")) {
			Channel current = new Channel(null, p.dst_ip.getHostAddress() + p.dst_port);
			int index = channels.indexOf(current);
			if(index != -1) {
				channels.get(index).addPrivmsg();
			}
		}
	}

	public void printWorkWeights() {
		ArrayList<Host> tmp = hosts;
		Iterator<Host> itr = tmp.iterator();
		while(itr.hasNext()) {
			Host h = itr.next();
			System.out.println(h.getIp() + ": " + h.printWorkWeight());
		}
	}
	
	public void printPacketCounts() {
		Iterator<Host> itr = ((ArrayList<Host>) hosts.clone()).iterator();
		while(itr.hasNext()) {
			Host h = itr.next();
			System.out.println(h.getIp() + ": " + h.printPacketCounts());
		}
	}

	public void printIRC() {
		Iterator<Channel> itr = ((ArrayList<Channel>) channels.clone()).iterator();
		while(itr.hasNext()) {
			Channel c = itr.next();
			System.out.println(c.getChannel());
		}
	}
}
