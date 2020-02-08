package EchoService;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;

public class EchoClient {
    private String host;
    private int port;
    public EchoClient(String host,int port){
        this.host=host;
        this.port=port;
    }
    // 对应的连接逻辑
    public void start(){

        EventLoopGroup group=new NioEventLoopGroup();
        try{
            Bootstrap bootstrap=new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(new InetSocketAddress(host,port))
                    .handler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new EchoClientHandler());
                        }
                    });
            // 连接到服务端，connect 是异步连接，使用同步等待
            ChannelFuture channelFuture=bootstrap.connect().sync();
            // 阻塞直到客户端通道关闭
            channelFuture.channel().closeFuture().sync();

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            // 优雅的退出
            group.shutdownGracefully();

        }
    }
    public static void main(String[] args) {
        new EchoClient("127.0.0.1",8080).start();
    }
}
