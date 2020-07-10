
import java.lang.System;
// import java.io.IOException;
import java.net.*;
import java.io.*;

//
// This is an implementation of a simplified version of a command 
// line ftp client. The program always takes two arguments
//


public class CSftp
{
    static final int MAX_LEN = 255;
    static final int ARG_CNT = 2;
    private static Socket fdpSocketGlobal;
    private static PrintWriter outGlobal;
    private static BufferedReader inGlobal;
    private static boolean isErrorThrown = false;
    private static boolean isFeatures = false;
    private static boolean isDir = false;
    private static boolean isList = false;
    private static boolean isGet = false;
    private static String fileName = "";

    private static Host secondSocket = new Host();

    public static void main(String [] args)
    {
        byte cmdString[] = new byte[MAX_LEN];

        // Get command line arguments and connected to FTP
        // If the arguments are invalid or there aren't enough of them
        // then exit.

        if (args.length != ARG_CNT) {
            System.out.print("Usage: cmd ServerAddress ServerPort\n");
            return;
        }

        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);


        try (
                Socket fdpSocket = new Socket(hostName, portNumber);
                PrintWriter out = new PrintWriter(fdpSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(fdpSocket.getInputStream()));
        ) {

            fdpSocketGlobal = fdpSocket;
            outGlobal = out;
            inGlobal = in;


            for (int len = 1; len > 0;) {
                //System.out.print("csftp> ");
                // len = System.in.read(cmdString);
                if (len <= 0)
                    break;
                // Start processing the command here.

                BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
                String fromServer;
                String fromUser = "";


                while ((fromServer = in.readLine()) != null) {
                    if (isFeatures) {
                        System.out.println("<-- " + fromServer);
                        while ((fromServer = in.readLine()) != null) {
                            System.out.println("<-- " + fromServer);
                            if (fromServer.startsWith("211")) {
                                break;
                            }
                        }
                    } else if (isDir) {
                        System.out.println("<-- " + fromServer);
                        int statusCode = getServerStatusCodes(fromServer);
                        checkStatusCodes(statusCode, fromServer);

                    } else if(isGet){
                        int statusCode = getServerStatusCodes(fromServer);
                        System.out.println("<-- " + fromServer);
                        checkStatusCodes(statusCode, fromServer);
                    }
                    else {
                        int statusCode = getServerStatusCodes(fromServer);
                        System.out.println("<-- " + fromServer);
                        checkStatusCodes(statusCode, fromServer);
                    }

                    isFeatures = false;
                    isDir = false;
                    isList = false;
                    isGet = false;

                    //Print for user input
                    System.out.print("csftp> ");
                    if (len <= 0)
                        break;

                    try{
                        fromUser = stdIn.readLine().trim();
                    } catch (Exception e){
                        inputException();
                    }

                    if (fromUser != null) {

                        // Piazza question if user just hits enter reprint csftp
                        while (fromUser.equals("")) {
                            System.out.print("csftp> ");
                            fromUser = stdIn.readLine();
                            fromUser = fromUser.trim();
                        }
                    }

                    redirectInput(fromUser);

                    while (isErrorThrown) {
                        System.out.print("csftp> ");
                        fromUser = stdIn.readLine();
                        fromUser = fromUser.trim();
                        redirectInput(fromUser);
                    }

                }

                processingError("Client disconnected");
            }

        } catch (UnknownHostException e) {
            controlConnectionPortError(hostName,portNumber);
        } catch (IOException e) {
            controlConnectionIOError();
        }
    }

    public static void redirectInput(String fromUser){
        isErrorThrown = false;
        String[] inputs = fromUser.split(" ");
        int inputLen = inputs.length;
        String command = inputs[0];
        String valueInput = "";

        if (inputs.length > 1) {
            valueInput = inputs[1];
        }


        switch (command) {
            case "user":
                if(!checkArgLength(inputLen, 2)) break;
                processUsernameRequest(valueInput.toLowerCase(), inputLen);
                break;
            case "pw":
                if(!checkArgLength(inputLen, 2)) break;
                processPasswordRequest(valueInput.toLowerCase());
                break;
            case "quit":
                if (checkArgLength(inputLen, 1))
                    processQuitRequest(command);
                break;
            case "get":
                if(!checkArgLength(inputLen, 2)) break;
                fromUser = valueInput;
                processGetRequest(valueInput);
                break;
            case "features":
                if(!checkArgLength(inputLen, 1)) break;
                isFeatures = true;
                processFeaturesRequest(command);
                break;
            case "cd":
                if(!checkArgLength(inputLen, 2)) break;
                processCdRequest(valueInput.toLowerCase());
                break;
            case "dir":
                if(!checkArgLength(inputLen, 1)) break;
                isDir = true;
                processDirRequest(command);
                break;

            default:
                System.out.println("0x001 Invalid command.");
                isErrorThrown = true;
                break;
        }

    }

    private static void processQuitRequest(String fromUser) {
        outGlobal.println("QUIT");
        printClientMessage("QUIT");
    }

    private static void processDirRequest(String fromUser) {
        outGlobal.println("PASV");
        printClientMessage("PASV");
        isList = true;
    }

    private static void callList(){
        outGlobal.println("LIST");
        printClientMessage("LIST");
    }



    private static void processCdRequest(String fromUser) {
        outGlobal.println("CWD " + fromUser);
        printClientMessage("CWD");
    }

    private static void processFeaturesRequest(String fromUser) {
        outGlobal.println("FEAT");
        printClientMessage("FEAT");
    }

    private static void processGetRequest(String valueInput) {
        isGet = true;
        fileName = valueInput;
        outGlobal.println("PASV");
        outGlobal.println("RETR " + valueInput);
    }

    private static void processUsernameRequest(String valueInput, int inputLen) {
        outGlobal.println("USER " + valueInput);
        printClientMessage("USER");
    }

    private static void processPasswordRequest(String valueInput) {
        outGlobal.println("PASS " + valueInput);
        printClientMessage("PASS");
    }


    private static void printClientMessage(String input){
        System.out.println("--> " +input);
    }


    //Checks that there are no arguments after the command
    private static boolean noArgumentsCheck(String input, String expected){
        return input.equals(expected);
    }

    //Check that there are arguments
    private static boolean checkArgLength(int inputLen, int correctLen) {
        if(inputLen == correctLen) {
            isErrorThrown = false;
            return true;
        }
        wrongNumArguments();
        return false;
    }

    private static int getServerStatusCodes(String serverResponse) {
        String[] splited = serverResponse.split(" ");

        int statusCode = Integer.parseInt(splited[0]);


        return statusCode;

    }


    private static void checkStatusCodes(int statusCode, String response) {

        Host secondConnection = new Host();

        switch (statusCode) {
            case 120:
//				System.out.println("Successful connection established");
                break;
            case 220:
//				System.out.println("Successful connection established");
                break;
            case 211:
//                System.out.println("Features");
                break;
            case 421:
//				System.out.println("Successful connection established");
                break;
            case 227:
                parseIPNetwork(response);
                openSecondSocket(secondSocket.hostIp, secondSocket.port);
                break;
            case 331:
//				System.out.println("Please specify password");
                break;
            case 230:
                outGlobal.println("TYPE L 8");
                try {

                    System.out.println("<-- " + inGlobal.readLine());

                } catch (IOException e) {
                    dataTransferIOError();
                }

                break;
            case 530:
//				System.out.println("anonymous only");
                break;
            case 550:
//                System.out.println("<-- " + response);
//                dataTransferIOError();
//				System.out.println("anonymous only");
                break;
            case 150:
//				System.out.println("Directory listing");
                break;
            case 226:
//				System.out.println("Directory listing ok");
                break;
            case 221:
                System.exit(1);
            default:
//				System.out.println("Unknown status code");
                break;
        }


    }


    private static void parseIPNetwork(String serverResponse) {

        String net = serverResponse.split("\\(")[1].split("\\)")[0];

        String[] netNums = net.split("\\,");
        String ip = "";
        int port = 0;

        for (int i = 0; i < 4; i++) {
            if (i == 0)
                ip += netNums[i];
            else {
                ip = ip + "." + netNums[i];
            }

        }

        port = Integer.parseInt(netNums[4])* 256 + Integer.parseInt(netNums[5]);

        secondSocket.hostIp = ip;
        secondSocket.port = port;
    }

    private static void openSecondSocket(String ip, int port){
        try (
                Socket secondSocket = new Socket(ip, port);
                PrintWriter secondOut = new PrintWriter(secondSocket.getOutputStream(), true);
                BufferedReader secondIn = new BufferedReader(
                        new InputStreamReader(secondSocket.getInputStream()));
                BufferedInputStream secondStream = new BufferedInputStream(secondSocket.getInputStream());
        ) {


            if(isList){
                callList();
                String fromServer = inGlobal.readLine();
                int statusCode = getServerStatusCodes(fromServer);
                if (statusCode == 150) {
                    System.out.println("<-- " + fromServer);
                    String dirListing = null;
                    while ((dirListing = secondIn.readLine()) != null) {
                        System.out.println(dirListing);
                    }
                    fromServer = inGlobal.readLine();
                    System.out.println("<-- " + fromServer);
                } else {
                    System.out.println("<-- " + fromServer);
                }
            }

            if(isGet){
                String fromServer;
                while((fromServer = inGlobal.readLine()) != null){
                    int statusCode = getServerStatusCodes(fromServer);
                    System.out.println("<-- " + fromServer);

                    if(statusCode == 226 || statusCode == 550){
                        break;
                    }

                    if(statusCode == 150) {
                        String fromSecond;
                        File filePath = new File(fileName);
                        filePath.createNewFile();

                        OutputStream os = null;

                        try{
                            os = new FileOutputStream(filePath);
                        } catch (FileNotFoundException e){
                            processingError("File not found");
                        }



                        try {

                            byte[] curr = secondStream.readAllBytes();
                            os.write(curr);
                            os.close();

                        } catch (IOException e) {
                            dataTransferIOError();
                        }


                    }

                }
            }

        } catch(UnknownHostException u)
        {
            if(isGet){
                dataTransferPortError(ip, port);
            } else {
                controlConnectionPortError(ip, port);
            }
        }
        catch(IOException i)
        { if(isGet){
            dataTransferIOError();
        } else{
            controlConnectionIOError();
        }
        }

    }


    private static void wrongNumArguments(){
        System.err.println("0x002 Incorrect number of arguments.");
        isErrorThrown = true;
    }

    private static void controlConnectionPortError(String host, int port){
        String currPort = Integer.toString(port);
        System.err.println("0xFFFC Control connection to " + host + " on port " + currPort + " failed to open.");
        System.exit(1);
    }

    private static void controlConnectionIOError(){
        System.err.println("0xFFFD Control connection I/O error, closing control connection.");
        System.exit(1);
    }

    private static void dataTransferPortError(String host, int port){
        String currPort = Integer.toString(port);
        System.err.println("0x3A2 Data transfer connection to " + host + " on port " + currPort + " failed to open.");
        isErrorThrown = true;
    }

    private static void dataTransferIOError(){
        System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
        isErrorThrown = true;
    }

    private static void inputException(){
        System.err.println("0xFFFE Input error while reading commands, terminating.");
        System.exit(1);
    }

    private static void processingError(String err){
        System.err.println("0xFFFF Processing error." + err);
        System.exit(1);
    }

}

class Host {
    public String hostIp;
    public int port;
}



