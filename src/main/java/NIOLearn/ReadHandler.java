package NIOLearn;

import java.nio.channels.SocketChannel;
import java.util.LinkedList;

public abstract class ReadHandler {

    // 存放接收的字节数组
    protected LinkedList<byte[]> dataList = new LinkedList<byte[]>();
    // 存放读取的字节数
    protected int readCount = 0;
    // 存放未处理完成的包
    protected LinkedList<MsgPacket> packetQueue = new LinkedList<MsgPacket>();
    // 对应的SocketChannel
    protected SocketChannel socketChannel;

    public ReadHandler() {}

    public ReadHandler(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    public synchronized void read(byte[] data) {
        dataList.add(data);
        readCount = readCount + data.length;
    }

    public synchronized void handle() {
        if(readCount == 0) {
            return ;
        }
        boolean flag = true;
        byte[] bytes = toBytes();
//		System.out.println("handle data = " + new String(bytes));
        while (flag) {
            switch (bytes[0]) {
                case MsgPacket.MSG_FLAG:
                    if (bytes.length < MsgPacket.HEADER_SIZE) {
                        // 此时连包的header都没有接收完
                        MsgPacket msgPacket = new MsgPacket(bytes);
                        packetQueue.add(msgPacket);
                        flag = false;
                    } else {
                        byte[] header = new byte[MsgPacket.HEADER_SIZE];
                        header[0] = MsgPacket.MSG_FLAG;
                        header[1] = bytes[1];
                        header[2] = bytes[2];
                        MsgPacket msgPacket = new MsgPacket(header);
                        bytes = adjust(bytes, bytes.length - header.length);
                        if (bytes.length - header.length == 0) {
                            // 刚好是接收了一个包的header部分
                            packetQueue.add(msgPacket);
                            flag = false;
                        } else {
                            int more = bytes.length;
                            int length = bytes.length > msgPacket.getNeedDataLength() ? msgPacket.getNeedDataLength() : bytes.length;
                            byte[] newdata = new byte[length];
                            // 如果除header外剩余的数据大于msgPacket中记录的数据长度,那么证明还有剩余的包
                            System.arraycopy(bytes, 0, newdata, 0, length);
                            msgPacket.read(newdata);
                            if (msgPacket.isCompleted()) {
                                // 包完整地处理了,处理响应动作
                                response(msgPacket);
                                // 处理完一个包,计算剩余长度
                                more = more - length;
                                if (more > 0) {
                                    // 还有其他包数据,重新调整bytes数组
                                    bytes = adjust(bytes, more);
                                } else { // 刚好是处理一个包的长度
                                    flag = false;
                                }
                            } else {
                                // 包的数据部分接收不完整
                                // 放到队列再进行处理
                                packetQueue.add(msgPacket);
                                flag = false;
                            }
                        }
                    }
                    break;
                default:
                    // 发送的数据包括上一次未完成的部分,接着处理前面未处理完的包
                    if(!packetQueue.isEmpty()) {
                        MsgPacket msgPacket = packetQueue.get(0);
                        // 可能的情况: 1.不知道包长度(header没有接受完整) 2.知道包长度
                        if(!msgPacket.isHeaderCompleted()) {	// 包header没有接收完整
                            // 包的header部分接收不完整
                            byte[] unCompletedHeader = msgPacket.getHeader();
                            // 现有header长度
                            int curLength = unCompletedHeader.length;
                            byte[] header = new byte[MsgPacket.HEADER_SIZE];
                            // 需要补全的header长度
                            int needLength = header.length - unCompletedHeader.length;
                            // 复制原来的
                            System.arraycopy(unCompletedHeader, 0, header, 0, curLength);
                            // 加上不足的
                            System.arraycopy(bytes, 0, header, curLength, needLength);
                            // 重新设置header,计算数据长度和包长度
                            msgPacket.resetHeader(header);
                            msgPacket.calLength();
                            // 重新调整bytes数组(去掉header)
                            int more = bytes.length - needLength;
                            bytes = adjust(bytes, more);
                        }
                        int more = bytes.length;
                        int length = bytes.length > msgPacket.getNeedDataLength() ? msgPacket.getNeedDataLength() : bytes.length;
                        byte[] newdata = new byte[length];
                        // 如果除header外剩余的数据大于msgPacket中记录的数据长度,那么证明还有剩余的包
                        System.arraycopy(bytes, 0, newdata, 0, length);
                        msgPacket.read(newdata);
                        if (msgPacket.isCompleted()) {

                            // 包完整地处理了,进行相关响应动作
                            response(msgPacket);

                            // 退出队列
                            packetQueue.remove(0);

                            // 处理完一个包,计算剩余长度
                            more = more - length;
                            if (more > 0) {
                                // 还有其他包数据,重新调整bytes数组
                                bytes = adjust(bytes, more);
                            } else { // 刚好是处理一个包的长度
                                flag = false;
                            }
                        } else {
                            // 包的数据部分接收不完整
                            // 本来就已经在队列里面了 不需要再add
//						packetQueue.add(msgPacket);
                            flag = false;
                        }
                    }
                    break;
            }
        }
    }

    // 将收到的字节转换为一个数组,进行处理
    private byte[] toBytes() {
        byte[] bytes = new byte[readCount];
        int destPos = 0;
        while(!dataList.isEmpty()) {
            byte[] bytes0 = dataList.remove(0);
            System.arraycopy(bytes0, 0, bytes, destPos, bytes0.length);
            destPos = destPos + bytes0.length;
        }
        readCount = 0;
        return bytes;
    }

    // 调整bytes中剩余的字节到新的数组,并返回
    protected byte[] adjust(byte[] bytes, int more) {
        byte[] tmp = new byte[more];

        int completed = bytes.length - more;
        // 从后往前复制
        for(int i = bytes.length - 1,j = tmp.length - 1; i >= completed; i--,j--) {
            tmp[j] = bytes[i];
        }
        return tmp;
    }

    // 收到数据后的响应动作
    protected abstract void response(MsgPacket msgPacket);

}