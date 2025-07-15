/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tftp.udp.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class TFTPUDPServer {
        
    DatagramSocket socket = null;
    
    
    
    
    public static void main(String[] args) throws IOException {
        // creates the server
        TFTPUDPServer tftpudpserv = new TFTPUDPServer();
        
    }
    
    public TFTPUDPServer() throws SocketException, IOException{
        socket = new DatagramSocket(2222); // initially set to 1025 but was taken on my machine did random port to get it to start
        byte[] recvBuf = new byte[516];
        //try mainly from UDP lab
        try{
            while(true){
                DatagramPacket packet = new DatagramPacket(recvBuf, 516);
                socket.receive(packet);
                new Thread(new TFTPUDPhandler(packet)).start(); //starts thread from handler
                
                //removed date/time didn't know if was needed
                
                //Extracts IP and port
                InetAddress addr = packet.getAddress();
                int srcPort = packet.getPort();
                
                //Sets buffer as data
                packet.setData(recvBuf);
                
                //Sets IP and port as destination
                packet.setAddress(addr);
                packet.setPort(srcPort);
                
                //Sends packet
                socket.send(packet);
            }
        } catch (IOException e) { //exception handling
            System.err.println(e);
        }
        socket.close();
    } 
 

    
}
