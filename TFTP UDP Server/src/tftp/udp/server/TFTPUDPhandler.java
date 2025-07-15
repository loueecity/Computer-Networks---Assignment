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
import java.util.Random;
import java.io.ByteArrayOutputStream;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

//
public class TFTPUDPhandler implements Runnable {
    InetAddress addr; // holds ip address
    int port =2222; //holds port
    DatagramSocket socket= null;
    
    DatagramPacket recv; //packet received
    DatagramPacket snt; //packet sent
    
    //Random is not used anymore after trying to fix the communication between the server and client
    Random r = new Random(); // needed to generate port
    int portNo = r.nextInt(65535 - 1024) + 1024; // Selects random port above 1024
    
    //packet op codes as specified in the protocol
    byte opcode_rrq =1; //read request
    byte opcode_wrq =2; //write request
    byte opcode_data =3; //data packet
    byte opcode_ack =4; //acknowledgement packet
    byte opcode_error=5; //error packet
    byte request; //byte to hold req type
    
    
    /**
     * Constructor method
     * @param packet - Receives packet from the server
     * @throws SocketException 
     */
    public TFTPUDPhandler(DatagramPacket packet) throws SocketException{
        recv = packet; //received packet is the parameter
        socket = new DatagramSocket(2222); // selects random port
        socket.setSoTimeout(5000); // 5000ms / 5 seconds timeout
    }
    
    /**
     * getFileName gets the file name from the packet
     * Builds string using string builder to build the filename from the bytes
     * @param recvBuf
     * @return file name from the data
     */
    public String getFileName(byte[] recvBuf){
        final int length = recvBuf.length; //length of the received buffer
        StringBuilder sb = new StringBuilder(); //stringbuilder
        
        //index starting at 2 so it is not the opcode
        int i = 2;
        byte b;
        
        //while loop to get the bytes to turn into the file name
        while ( (i<length)&& (b = recvBuf[i]) !=0){
           int v = ((int)b) & 0xff;
           sb.append((char) v);
           i++;
        }
        return sb.toString(); //returns the file name
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
    
    
    @Override
    public void run(){
        
        
        int sentBlock= 0; // The block being sent at the time
        int blockExpected = 0; //Expceted block number 
        int blockCount =0; //How much has already been sent
        byte[] write = {}; //Data to be written
        String file = ""; //Empty string to hold the name of the file
        ByteArrayOutputStream out = new ByteArrayOutputStream(); //For the output
        
        try {
            //runs forever
            Boolean finished = false; //  to track if all data has been sent
            Boolean finalPacket = false; // boolean to tell if it is the last packet to be sent
            
            //When it is not the final packet being sent
            while (finalPacket == false){
                addr = recv.getAddress(); //gets the ip address of the received packet
                port = recv.getPort(); //gets the port of the received packet
                byte[] recvBuf = recv.getData(); //gets the data from the packet 
                byte[] opCode = {recvBuf[0], recvBuf[1]}; //to get the opcode from the packet
                
                //if the byte opcode is equal to the byte opcode_rrq
                if (opCode[1] == opcode_rrq){
                    file = getFileName(recvBuf); //gets name of file from the buffer
                    request = opcode_rrq;
                    try {
                        write= readFile(file);} // reads the data to be given
                    catch (FileNotFoundException e){ //exception handling
                        //error messages + sending error packet
                        sendErrorPacket("File not found");
                        e.printStackTrace();
                        System.err.println("File not found error");
                        break;}
                    
                    //Incrementing 
                    byte[] blockNum = incrBlock(sentBlock); 
                    sentBlock++; 
                    
                    ByteArrayOutputStream blockCont = new ByteArrayOutputStream(); //For sending the data
                    
                    //Variables to be used
                    int numToBeSent = write.length; int numLeft;
                    
                    //Calculating how much to be sent
                    if (((numToBeSent - blockCount) / 512) >= 1) { //If more than one block
                        numLeft = 512;
                    } else { //less than one block
                        numLeft = (numToBeSent - blockCount);                       
                    }
                    int sentLoop = blockCount; 
                    
                    for (int i = blockCount; i < (blockCount + numLeft); i++) { 
                        blockCont.write(write[i]); //Writing the content
                        sentLoop++; //to keep track of how much has been sent
                    }
                    blockCount = sentLoop; //assigning how much has been sent
                    
                    
                    //Simple if to see if everything has been sent/finished
                    if (blockCount == numToBeSent) {
                        finished = true;
                    }

                    byte[] sendPacket = blockCont.toByteArray(); //byte array to hold the data to be sent in the packet
                    byte[] data = {0, opcode_data, blockNum[0], blockNum[1]};
                    byte[] buffer = new byte[data.length + sendPacket.length];
                    System.arraycopy(data, 0, buffer, 0, data.length); //In accordance with RFC 1350 headers
                    System.arraycopy(sendPacket, 0, buffer, data.length, sendPacket.length); //                
                    snt = new DatagramPacket(buffer, buffer.length, addr, port); //Creating the packet with the information to be sent and destination
                    socket.send(snt); //sending the packet through the socket

                    if (blockCount == numToBeSent) {
                        if (numToBeSent % 512 == 0) { 
                            blockNum = incrBlock(sentBlock); 
                            sentBlock++;
                            byte[] emptyPacket = {0, opcode_data, blockNum[0], blockNum[1]};
                            snt = new DatagramPacket(emptyPacket, emptyPacket.length, addr, recv.getPort());
                            socket.send(snt);
                            finalPacket = true;
                        }
                    }
                    
                    //ack packet
                   } else if (opCode[1] == opcode_ack) { 
                    request = opcode_ack;
                       if (finished == true) { 
                        break; //If it's finished it can break
                    } 
                    else {
                        ByteArrayOutputStream toBeWritten = new ByteArrayOutputStream();
                        byte[] blockNum = incrBlock(sentBlock);  //incrementing the block to be sent
                        sentBlock++;
                        int numToBeSent = write.length; //length to be sent
                        int numLeft;//left to send
                        //Repeating earlier to see if the data is fully sent in one block
                        if ((numToBeSent - blockCount) / 512 >= 1) {
                            numLeft = 512;} 
                        else {
                            numLeft = (numToBeSent - blockCount);}

                        int blocksSent = blockCount;
                        for (int i = blockCount; i < (blockCount + numLeft); i++) {
                            toBeWritten.write(write[i]);
                            blocksSent++; //incr
                        }
                        blockCount = blocksSent;
                        
                        if (blockCount == numToBeSent) {
                            finished = true;    
                        }
                        //Copying arrays of data for the packet to be RFC 1350 compliant 
                        byte[] toBeSent = toBeWritten.toByteArray();
                        byte[] startOfData = {0, opcode_data, blockNum[0], blockNum[1]};
                        byte[] data = new byte[toBeSent.length + startOfData.length];
                        System.arraycopy(startOfData, 0, data, 0, startOfData.length); 
                        System.arraycopy(toBeSent, 0, data, startOfData.length, toBeSent.length);
                        snt = new DatagramPacket(data, data.length, addr, recv.getPort());
                        socket.send(snt); // sending the packet
                        
                        //Sending = finished
                        if (blockCount == numToBeSent && numToBeSent % 512 == 0) {  
                            blockNum = incrBlock(sentBlock); 
                            sentBlock++;
                            byte[] emptyPacket = {0, opcode_data, blockNum[0], blockNum[1]};
                            snt = new DatagramPacket(emptyPacket, emptyPacket.length, addr, recv.getPort());
                            socket.send(snt);
                            finished = true;}
                    }} 
                        //wrq
                        else if (opCode[1] == opcode_wrq) { 
                        file = getFileName(recvBuf); //gets file name
                        request = opcode_wrq; //holds req 
                        byte[] blockNum = {0, 0}; //starting from 0
                        byte[] ack = {0, opcode_ack, blockNum[0], blockNum[1]}; //to ack to send data
                        snt = new DatagramPacket(ack, ack.length, addr, port); //building packet
                        socket.send(snt);} //sending packet
                        
                        //if data
                        else if (opCode[1] == opcode_data) { 
                        request = opcode_data; //holds req
                        byte[] blockNum = {recvBuf[2], recvBuf[3]}; //where data is held
                        byte[] expectedBlockNum = incrBlock(blockExpected); //incr
                        
                        //For ack
                        if (blockNum[0] == expectedBlockNum[0] && blockNum[1] == expectedBlockNum[1]) {
                        blockExpected++; //incr
                        out.write(recv.getData(), 4, recv.getLength() - 4); //data from byte
                        byte[] ack = {0, opcode_ack, blockNum[0], blockNum[1]}; //ack byte data
                        snt = new DatagramPacket(ack, ack.length, addr, port); //building packet
                        socket.send(snt); //sending packet
                    } else {
                        //If it hits this else it is an error
                        request = opcode_error; //updating req
                        sendErrorPacket("Failed sending"); //Error packet
                        System.err.println("Error");} //printing to console
                } 
                
                //Last packet being sent
                if (recvBuf[1] == opcode_data && recv.getLength() < 516) { 
                    finalPacket = true;} //set to true 
                    else{    
                        try {
                             socket.receive(recv);} 
                        catch (SocketTimeoutException e) {                        
                            e.printStackTrace(); 
                            socket.send(snt);}
                    }
                } 
            
                } catch (IOException e) {
                  System.err.println(e);}

                if (request == opcode_wrq) { 
                    try {
                    writeFile(file, out);} 
                    catch (IOException e) {
                    System.err.println(e);}
                }
        //Closing socket at end of run
        socket.close();
    }
    
   /**
    * Used to increment the block when data is being sent 
    * @param blockNum
    * @return the incremented block number
    */
    public byte[] incrBlock(int blockNum) {
        blockNum++; //incr
        byte[] b = new byte[2]; //data byte
        b[0] = (byte) (blockNum & 0xFF); // First 8 bits
        b[1] = (byte) ((blockNum >> 8) & 0xFF); // Next 8 bits to increment 
        return b; //returning the incr byte
    }
    
    /**
     * For sending an error packet
     * @param errordesc
     * @throws IOException 
     */
    public void sendErrorPacket(String errordesc) throws IOException{
        byte[] errordescByte = errordesc.getBytes("US-ASCII"); //charset US-ASCII for the error message
        byte[] errorCode ={0,1};
        byte[] error = {0, opcode_error, errorCode[0], errorCode[1]};
        byte[] errorv = new byte[error.length + errordescByte.length +1];
        System.arraycopy(error,0, errorv, 0, error.length);
        System.arraycopy(errordescByte,0,errorv,error.length,errordescByte.length);
        errorv[error.length + errordescByte.length] = (byte) 0;
        DatagramPacket errorPacket = new DatagramPacket(errorv, errorv.length, addr, recv.getPort());
        
        socket.send(errorPacket);
    }
}
