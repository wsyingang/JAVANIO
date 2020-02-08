package NIOLearn;

/**
 * 该包用于封装发送的消息和读取发送的消息
 * @author CrazyPig
 *
 */
public class MsgPacket {

    public static final byte MSG_FLAG = 0x01;
    public static final int HEADER_SIZE = 3;

    private byte[] header = new byte[HEADER_SIZE];	// 第一个字节为包类型,后面两个字节表示数据长度
    private byte[] data;	// 数据
    private int length;	// 包长度

    private int curDataLength = 0;	// 当前data[]长度

    public byte[] getData() {
        return this.data;
    }
    public byte[] getHeader() {
        return this.header;
    }
    public int getPacketLength() {
        return this.length;
    }
    public int getDataLength() {
        return this.length - header.length;
    }
    public int getCurDataLength() {
        return this.curDataLength;
    }

    // 获取还需要填充的数据字节长度
    public int getNeedDataLength() {
        return this.getDataLength() - this.getCurDataLength();
    }

    // 根据发送的消息构造一个包
    public MsgPacket(String msg) {
        this.data = msg.getBytes();
        this.length = data.length + header.length;
        int dlen = data.length;
        genHeader(dlen);
    }

    // 根据收到的header构造一个包
    public MsgPacket(byte[] header) {
        this.header = header;
        if(header.length < HEADER_SIZE) {
            return ;
        }
        // 求数据长度
        calLength();
    }

    // 求数据长度
    public void calLength() {
        byte high = (byte) ((header[1] << 8) & 0xff00);
        byte low = (byte) (header[2] & 0x00ff);
        int dataLength = (high | low);
        this.data = new byte[dataLength];
        this.length = dataLength + header.length;
    }

    public void read(byte[] newdata) {
        // 复制newdata[]数组内容到data[]数组
        System.arraycopy(newdata, 0, this.data, this.curDataLength, newdata.length);
        this.curDataLength += newdata.length;
    }

    // 判断包是否完整
    public boolean isCompleted() {
        int curLength = this.curDataLength + this.header.length;
        return curLength == this.length;
    }

    // 判断header是否完整
    public boolean isHeaderCompleted() {
        return header.length == HEADER_SIZE;
    }

    public void genHeader(int dataLength) {
        header[0] = MSG_FLAG;
        // header[1] 高字节
        header[1] = (byte) ((dataLength & 0xff00) >> 8);
        header[2] = (byte) (dataLength & 0x00ff);
    }

    // 返回整个包的数据
    public byte[] getBytes() {
        byte[] allBytes = new byte[this.length];
        System.arraycopy(header, 0, allBytes, 0, this.header.length);
        System.arraycopy(data, 0, allBytes, this.header.length, this.data.length);
        return allBytes;
    }

    // 设置header
    public void resetHeader(byte[] header) {
        this.header = header;
    }

}