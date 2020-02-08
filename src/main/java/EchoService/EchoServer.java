package EchoService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class EchoServer {
    private int port;
    public EchoServer(int port){
        this.port=port;
    }
    public void run() throws InterruptedException {
        // 两个线程组 配置服务端线程组
        EventLoopGroup bossGroup= new NioEventLoopGroup();// 默认的创建内核数量*2
        EventLoopGroup workGroup=new NioEventLoopGroup();
        try {
            // 需要一个启动的引导类
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // pipeline 就是一个流水线，可以有很多和handler，用来处理对应的逻辑
                            ch.pipeline().addLast(new EchoServerHandler());
                        }
                    });
            System.out.println("Echo 服务启动ing");
            // 绑定端口，同步等待
            ChannelFuture channelFuture = serverBootstrap.bind(this.port).sync();
            // 等待服务端监听端口关闭
            channelFuture.channel().closeFuture().sync();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            // 释放线程池
            workGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
    public static void main(String[] args) throws InterruptedException {
        int port=8080;
        if(args.length>0){
            port=Integer.parseInt(args[0]);
        }
        new EchoServer(port).run();
    }
}
