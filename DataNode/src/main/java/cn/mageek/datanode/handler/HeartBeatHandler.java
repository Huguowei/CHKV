package cn.mageek.datanode.handler;

import cn.mageek.common.model.HeartbeatResponse;
import cn.mageek.datanode.job.DataTransfer;
import cn.mageek.datanode.res.JobFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

import static cn.mageek.common.res.Constants.offlineKey;
import static cn.mageek.common.res.Constants.offlineValue;
import static cn.mageek.datanode.main.DataNode.DATA_POOL;

/**
 * @author Mageek Chiu
 * @date 2018/5/7 0007:13:52
 */
public class HeartBeatHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(HeartBeatHandler.class);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("opened connection to: {}",ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("closed connection: {}",ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        HeartbeatResponse response = (HeartbeatResponse) msg;// 因为这个in 上面的 in 是decoder，所以直接可以获得对象
        logger.debug("DataNode received: {}",response);
        handleResponse(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("connection to: {}，error: ",ctx.channel().remoteAddress(),cause);
        ctx.close();
    }

    private void handleResponse(HeartbeatResponse response){
        DataTransfer dataTransfer = null;
        String IPPort = response.getIPPort();
        if (response.isOk()){// 继续运行
            if (IPPort!=null){
                logger.info("DataNode 需要转移部分数据给上一个节点");
                dataTransfer = (DataTransfer) JobFactory.getJob("DataTransfer");
                dataTransfer.connect(IPPort,false);
            }else{
                logger.debug("DataNode 不需要转移数据");
            }
        }else{// 不再运行
            if (IPPort!=null){
                logger.info("DataNode 数据全部迁移给下一个节点,{}",IPPort);
                dataTransfer = (DataTransfer) JobFactory.getJob("DataTransfer");
                dataTransfer.connect(IPPort,true);
            }else{
                logger.info("DataNode 最后一台下线，不需要转移数据");
                DATA_POOL = null;
            }
        }

        if(dataTransfer != null){
            Thread transfer = new Thread(dataTransfer,"dataTransfer");
            transfer.start();// 新起一个线程，但是这样可能不稳定，待改进
        }
    }

}
