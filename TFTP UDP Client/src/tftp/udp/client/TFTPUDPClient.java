/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tftp.udp.client;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;



public class TFTPUDPClient {

    InetAddress addr; // holds ip address
    int port=1234;//holds port
    int destPort = 2222;
    DatagramSocket socket= null;
    final int packetSize = 516;
    DatagramPacket outB; //outbound packet used
    DatagramPacket inB;
    byte[] buffer;
    //random not used any more when trying to fix
    //Random r = new Random(); // needed to generate port
    //int portNo = r.nextInt(65535 - 1024) + 1024; // Selects random port above 1024 //This is no longer used as it wasn't working so tried to set with a given port to work
    boolean isError=false;
    //packet op codes as specified in the protocol
    byte opcode_rrq =1; //read request
    byte opcode_wrq =2; //write request
    byte opcode_data =3; //data packet
    byte opcode_ack =4; //acknowledgement packet
    byte opcode_error=5; //error packet
    byte request; //byte to hold req type
    byte[] toWrite= {};
    
    
    /**
     * This creates the client by calling the constructor and setting the socket for the client
     * Also sets what type the request is from the args, if it is not 1 or 2 then an error is given + client shut
     * @param args the command line arguments IP/Request type/File name
     */
    public static void main(String[] args) throws Exception, IOException, SocketException {
        //Using args

        //Creating client
        TFTPUDPClient client = new TFTPUDPClient();
        //client.addr = InetAddress.getLocalHost(); // Might be easier to use localhost instead of getting ip from args? should communicate flawlessly on same machine?
        
        //if args not in right format it will not work so prompts for correct args
        if (args.length <1 || args.length >2){
            System.out.println("Arguments must be 1))Request type (1 or 2) 2)File name");
            return;
        }
        
        //gets file name
        String file = args[1];
        //Changes the requests based on the args
        //To be used in later methods
        if ("1".equals(args[0])){
            client.request = 1;
        }else if ("2".equals(args[0])){
            client.request = 2;
        }else {
           System.out.println("Please enter valid op code. Refer to RFC 1350");
           System.out.println("opcode 1 = read request , opcode 2 = write request");
           System.exit(0);
        }
        client.get(file); //Handles the transaction
    }
    
    /**
     * Simple constructor for the client
     */
    public TFTPUDPClient(){
        try{
            this.socket= new DatagramSocket();
        }catch(SocketException e){
            e.printStackTrace();
        }
    }
    
    /**
     * Gets the file for the client /handles the transaction
     * @param fileName
     * @throws IOException
     * @throws SocketException 
     */
     public void get(String fileName) throws IOException, SocketException{
        //addr = InetAddress.getByName(args[0]); //gets addr from IP in args
        addr = InetAddress.getLocalHost(); //gets local ip
        String file = fileName; //file name from args
        socket = new DatagramSocket(); //new socket
        byte[] reqByte = createReq(request, file); //creates the request block
        outB = new DatagramPacket(reqByte,reqByte.length, addr, destPort); //sends request
        ByteArrayOutputStream BOS=receive(); //receives (tries to receive?)
        writeFile(fileName,BOS); //writes file to the disk
     }
    
    
    /**
     * ********This is broken*********
     * **FIXED?
     * Should create the byte array to be used when requesting the block
     * @param req the request byte given from the initial args
     * @param filename the file name given from the initial args
     * @return the request block
     * @throws UnsupportedEncodingException 
     */
    public byte[] createReq(byte req, String filename) throws UnsupportedEncodingException{
        String mode =  "octet";
        //below no longer needed
        //byte[] filenameB = filename.getBytes("US-ASCII");
        //byte[] modeB = mode.getBytes("US-ASCII");
        int rrqsize = (2 +filename.length() + 1 + mode.length() +1);
        byte[] rrq = new byte[rrqsize];
        byte zero = 0;
        
        //This might copy out of order ? if so then rest of code wont work as won't get header bit properly
        //System.arraycopy(filenameB,0, rrq, 0, filenameB.length);
        //System.arraycopy(modeB,0,rrq,0,modeB.length);
        //This is using a tutorial for a request after 
        int pos = 0; //this is used when incrementing
        rrq[pos] = zero; //uses zero byte as position
        pos++; //increments for next position
        rrq[pos] = req; //puts the request opcode in
        //puts the file name into the byte array
        for (int i=0; i<filename.length();i++){
            rrq[pos] = (byte)filename.charAt(i);
            pos++;
        }
        rrq[pos]=zero; //resets position to 0
        pos++; //increments
        //puts the mode octet into the btearray
        for(int i=0; i<mode.length();i++){
            rrq[pos]= (byte)mode.charAt(i);
            pos++;
        }
        rrq[pos]=zero; //resets pos
        return rrq; //returns this for the request
    }
    
    
    /**
     * This is for receiving the files from the server and to store the data to the client
     * @return
     * @throws IOException 
     */
    public ByteArrayOutputStream receive() throws IOException{
        ByteArrayOutputStream BOS = new ByteArrayOutputStream(); //creates bytearrayoutput
        int block=1; //keeps track of block being sent for client
        do{ 
            System.out.println("Sending block:" +block); //prints for user
            block++; //increments block
            buffer=new byte[packetSize];//new buffer with size 516
            inB= new DatagramPacket(buffer,buffer.length,addr,2222); //in bound datagram packet 
            
            socket.receive(inB); //receives the packet
            
            byte[] op = {buffer[0],buffer[1]}; //gets the op code
            
            //Checks if it is a data opcode
            if(op[1]==opcode_data){
                byte[] blockNo = {buffer[2],buffer[3]};
                DataOutputStream DOS = new DataOutputStream(BOS);
                DOS.write(inB.getData(),4,inB.getLength()-4);
                sendAck(blockNo);
            }
        }while(!isFinalPacket(inB));
        return BOS;
    }
    
    /**
     * Simple check to see if the packet is the last packet used in other methods
     * @param p the datagram packet being checked
     * @return 
     */
    public boolean isFinalPacket(DatagramPacket p){
        if(p.getLength() <512){
            return true;
        } else {
            return false;
        }
    }
    
    
    /**
     * Creates the acknowledgement packet to send
     * @param blockNum 
     */
    public void sendAck(byte[] blockNum){
        byte[] ack = {0, opcode_ack, blockNum[0], blockNum[1]};
        DatagramPacket ackP= new DatagramPacket(ack, ack.length, addr,2222);
        try{
            socket.send(ackP);
        } catch (IOException e){
            e.printStackTrace();
        }
    }
    /**
     * Reads data from a file 
     * @param file
     * @return Data of the file in a byte array
     * @throws IOException
     * @throws FileNotFoundException 
     */
    public byte[] readFile(String file) throws IOException, FileNotFoundException{
        Path path = Paths.get(file); //gets the file
        
        byte[] fileData = Files.readAllBytes(path); //holds byte array of data from file
        return fileData; //returns the byte array
    }
   
    /**
     * Writes data to a file
     * @param outFile
     * @param output
     * @throws IOException
     * @throws FileNotFoundException 
     */
    public void writeFile(String outFile, ByteArrayOutputStream output) throws IOException, FileNotFoundException{
        File file = new File(outFile);
        
        OutputStream opS= new FileOutputStream(file);
        opS.write(output.toByteArray());
    }
   
    
    
    
}
