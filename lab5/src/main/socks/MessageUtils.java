package main.socks;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;


public class MessageUtils {
    private static final int BUF_SIZE = 8192;
    private static final byte NO_AUTH = 0x00;
    private static final byte SOCKS5 = 0x05;
    private static final byte IPV4 = 0x01;
    private static final byte DOMEN = 0x03;
    private static final byte RESERVED = 0x00;
    private static final byte ERR = (byte) 0xFF;
    private static final byte[] LOCALHOST = new byte[]{0x7F, 0x00, 0x00, 0x01};



    public static boolean getFirstMessage(SocketChannel from) {
        ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
        try {
            if (from.read(buffer) == -1) {
                return false;
            } else {
                buffer.flip();
                byte socksVersion = buffer.get();
                byte methodAmount = buffer.get();
                byte[] methods = new byte[methodAmount];
                buffer.get(methods);
                return socksVersion == SOCKS5 && ArrayUtils.contains(methods, NO_AUTH);
            }
        } catch (IOException e) {
            return false;
        }
    }

    public static void sendFirstConfirmation(SocketChannel to, boolean accepted) throws IOException {
        ByteBuffer message = ByteBuffer.allocate(2);
        message.put(SOCKS5);
        if (accepted) {
            message.put(NO_AUTH);
        } else {
            message.put(ERR);
        }
        to.write(ByteBuffer.wrap(message.array(), 0, 2));
    }

    public static SecondMessage getSecondMessage(SocketChannel from) {
        ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
        try {
            from.read(buffer);
            buffer.flip();
            return new SecondMessage(buffer);
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
        return null;
    }

    public static void sendSecondConfirmationMessage(SocketChannel to, int port, boolean isNotError) throws IOException {
        byte[] resultMessage;
        if (isNotError) {
            byte OK = 0x00;
            resultMessage = ArrayUtils.addAll(new byte[] {SOCKS5, OK, RESERVED, IPV4}, LOCALHOST);
        } else {
            byte ERROR = 0x01;
            resultMessage = ArrayUtils.addAll(new byte[] {SOCKS5, ERROR, RESERVED, IPV4}, LOCALHOST);
        }
        resultMessage = ArrayUtils.addAll(resultMessage, (byte) ((port >> 8) & 0xFF), (byte) (port & 0xFF));
        to.write(ByteBuffer.wrap(resultMessage, 0, 10));
    }

    public static class SecondMessage {

        private byte version;
        private byte command;
        private byte addressType;
        private byte[] target;
        private byte[] port = new byte[2];

        SecondMessage(ByteBuffer bytes) {
            System.out.println(bytes);
            version = bytes.get();
            command = bytes.get();
            bytes.get();
            addressType = bytes.get();
            setTarget(bytes);
            bytes.get(port,0,2);
            //printInfo();
        }

        public byte getAddressType() {
            return addressType;
        }

        private void setTarget(ByteBuffer bytes) {
            switch (addressType) {
                case IPV4: {
                    target = new byte[4];
                    bytes.get(target, 0, 4);
                    break;
                }
                case DOMEN: {
                    byte len = bytes.get();
                    target = new byte[len];
                    bytes.get(target, 0, len);
                    break;
                }
                default: break;
            }
        }

        public String getTarget() {
            switch (addressType) {
                case IPV4: {
                    return target[0] + "." + target[1] + "." + target[2] + "." + target[3];
                }
                case DOMEN: {
                    return new String(target);
                }
                default: return "unknown";
            }
        }
        public int getPort() {
            return (port[1] & 0xFF) + (port[0] << 8);
        }


        private void printInfo() {
            System.out.println("version = " + version);
            System.out.println("command = " + command);
            System.out.println("addr type = " + addressType);
            System.out.println("target = " + Arrays.toString(target));
            System.out.println("port = " + Arrays.toString(port));
        }

    }
}