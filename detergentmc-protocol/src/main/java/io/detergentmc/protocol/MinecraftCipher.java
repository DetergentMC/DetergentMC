package io.detergentmc.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import javax.crypto.Cipher;

/**
 * クライアントとの間の AES/CFB8 暗号化（担当1：763 online-mode認証）。
 *
 * <p>Minecraft の暗号化はフレームの下のストリーム層である：長さ
 * プレフィックスも含めて全バイトが暗号化される。そのため Netty パイプ
 * ラインの先頭（{@link io.detergentmc.protocol.ClientProxy.ProxyLinkHandler}
 * より手前）に置き、ハンドラーが見るのは復号済みの平文フレーム、
 * ハンドラーが書く平文フレームはここで暗号化されてからソケットへ出る。
 * バニラ側（プロキシ⇔内部バニラ）は平文のまま（内部バニラは
 * offline-mode・loopback の信頼済み接続）。
 *
 * <p>暗号化は Encryption Response を受け取った直後に有効化する。
 * クライアントは応答を送った直後から暗号化を始めるため、それ以降の
 * 読み取りはすべてこの復号を通る。クライアントへ送る Login Success
 * 以降もすべて暗号化される。
 */
public final class MinecraftCipher {

    /** パイプラインでの復号ハンドラーの名前。 */
    public static final String DECRYPT_HANDLER_NAME = "detergentmc-decrypt";
    /** パイプラインでの暗号化ハンドラーの名前。 */
    public static final String ENCRYPT_HANDLER_NAME = "detergentmc-encrypt";

    private MinecraftCipher() {
    }

    /**
     * パイプラインの先頭に暗号化・復号ハンドラーを入れる（既に入って
     * いれば何もしない）。呼び出しは Netty のイベントループから行うこと。
     */
    public static void install(ChannelPipeline pipeline, byte[] sharedSecret) {
        if (pipeline.get(DECRYPT_HANDLER_NAME) != null
                || pipeline.get(ENCRYPT_HANDLER_NAME) != null) {
            return;
        }
        pipeline.addFirst(ENCRYPT_HANDLER_NAME, new EncryptHandler(sharedSecret));
        pipeline.addFirst(DECRYPT_HANDLER_NAME, new DecryptHandler(sharedSecret));
    }

    /** 暗号化が入っているか。 */
    public static boolean isInstalled(ChannelPipeline pipeline) {
        return pipeline.get(DECRYPT_HANDLER_NAME) != null
                || pipeline.get(ENCRYPT_HANDLER_NAME) != null;
    }

    /** 受信バイト列を AES/CFB8 で復号するハンドラー。 */
    public static final class DecryptHandler extends ChannelInboundHandlerAdapter {

        private final Cipher cipher;

        public DecryptHandler(byte[] sharedSecret) {
            this.cipher = MojangAuth.createDecryptCipher(sharedSecret);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                ctx.fireChannelRead(msg);
                return;
            }
            byte[] in = new byte[buf.readableBytes()];
            buf.readBytes(in);
            buf.release();
            byte[] out = update(in);
            ctx.fireChannelRead(Unpooled.wrappedBuffer(out));
        }

        private byte[] update(byte[] in) {
            try {
                byte[] out = cipher.update(in);
                return out == null ? new byte[0] : out;
            } catch (Exception e) {
                throw new IllegalStateException("受信データの復号に失敗しました。", e);
            }
        }
    }

    /** 送信バイト列を AES/CFB8 で暗号化するハンドラー。 */
    public static final class EncryptHandler extends ChannelOutboundHandlerAdapter {

        private final Cipher cipher;

        public EncryptHandler(byte[] sharedSecret) {
            this.cipher = MojangAuth.createEncryptCipher(sharedSecret);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (!(msg instanceof ByteBuf buf)) {
                ctx.write(msg, promise);
                return;
            }
            byte[] in = new byte[buf.readableBytes()];
            buf.readBytes(in);
            buf.release();
            byte[] out;
            try {
                out = cipher.update(in);
            } catch (Exception e) {
                promise.setFailure(new IllegalStateException("送信データの暗号化に失敗しました。", e));
                return;
            }
            ctx.write(Unpooled.wrappedBuffer(out == null ? new byte[0] : out), promise);
        }
    }
}
