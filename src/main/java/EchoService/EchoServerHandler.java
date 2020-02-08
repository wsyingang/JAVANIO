package EchoService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

public class EchoServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf data=(ByteBuf)msg;
        System.out.println("当前收到的数据是:"+data.toString(CharsetUtil.UTF_8));
        ctx.writeAndFlush(data);
    }
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        System.out.println("读取成功");

    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 捕获到异常时候的处理，这个时候需要记录日志什么的。
        cause.printStackTrace();
        ctx.close();
    }
}
