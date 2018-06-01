package cn.mageek.namenode.service;

import cn.mageek.namenode.handler.ClientWatcherHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static cn.mageek.namenode.main.NameNode.clientPort;
import static cn.mageek.namenode.main.NameNode.clientThread;
import static cn.mageek.namenode.main.NameNode.countDownLatch;

/**
 * 管理所有client
 * @author Mageek Chiu
 * @date 2018/5/7 0007:20:18
 */
public class ClientManager implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(DataNodeManager.class);

    public void run() {
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);//接收连接
        EventLoopGroup workerGroup = new NioEventLoopGroup(clientThread);//处理连接的I/O事件
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)//新建一个channel
                    .option(ChannelOption.SO_BACKLOG, 512)//最大等待连接
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast("ReadTimeoutHandler",new ReadTimeoutHandler(100));// in // 多少秒超时
                            p.addLast(new ObjectDecoder(2048, ClassResolvers.cacheDisabled(this.getClass().getClassLoader())));// in 进制缓存类加载器
                            p.addLast(new ObjectEncoder());// out
                            p.addLast(new ClientWatcherHandler());// in
                        }
                    });

            // Start the server. 采用同步等待的方式
            ChannelFuture f = b.bind(Integer.parseInt(clientPort)).sync();
            logger.info("ClientManager is up now and listens on {}", f.channel().localAddress());
            countDownLatch.countDown();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
            logger.info("ClientManager is down");

        } catch (InterruptedException e) {
            logger.error("ClientManager start error: ", e);
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}




