package app.nebulagram.ui;

import org.telegram.tgnet.*;
import org.telegram.tgnet.tl.TL_account;

/** Missing bot-only methods from Telegram's public MTProto schema (not Bot API HTTP). */
public final class NebulaBotApi {
    private NebulaBotApi() { }
    public static final class Profile extends TLObject {
        public String name, about, description;
        @Override public void readParams(InputSerializedData in, boolean exception) {
            name = in.readString(exception); about = in.readString(exception); description = in.readString(exception);
        }
    }
    public static final class GetProfile extends TLObject {
        public TLRPC.InputUser bot;
        public String language = "";
        @Override public void serializeToStream(OutputSerializedData out) {
            out.writeInt32(0xdcd914fd); out.writeInt32(bot == null ? 0 : 1);
            if (bot != null) bot.serializeToStream(out); out.writeString(language);
        }
        @Override public TLObject deserializeResponse(InputSerializedData in, int constructor, boolean exception) {
            if (constructor != 0xe8a775b0) throw new RuntimeException("Unexpected bots.BotInfo");
            Profile result = new Profile(); result.readParams(in, exception); return result;
        }
    }
    public static final class SetRights extends TLObject {
        public boolean channel;
        public TLRPC.TL_chatAdminRights rights;
        @Override public void serializeToStream(OutputSerializedData out) {
            out.writeInt32(channel ? 0x788464e1 : 0x925ec9ea); rights.serializeToStream(out);
        }
        @Override public TLObject deserializeResponse(InputSerializedData in, int constructor, boolean exception) { return TLRPC.Bool.TLdeserialize(in, constructor, exception); }
    }
    public static final class Connection extends TLObject {
        public String id;
        public long user;
        public int date, dc;
        public boolean disabled;
        public TL_account.TL_businessBotRights rights;
        public void readParams(InputSerializedData in, boolean exception) {
            int flags = in.readInt32(exception); disabled = (flags & 2) != 0;
            id = in.readString(exception); user = in.readInt64(exception); dc = in.readInt32(exception); date = in.readInt32(exception);
            if ((flags & 4) != 0) rights = TL_account.TL_businessBotRights.TLdeserialize(in, in.readInt32(exception), exception);
        }
    }
    public static final class GetConnection extends TLObject {
        public String id;
        public void serializeToStream(OutputSerializedData out) { out.writeInt32(0x76a86270); out.writeString(id); }
        public TLObject deserializeResponse(InputSerializedData in, int constructor, boolean exception) {
            if (constructor != 0x74ae4240 && constructor != 0x725b04c3) throw new RuntimeException("Unexpected connection response");
            Connection found = null;
            int count = vectorCount(in, exception);
            for (int i = 0; i < count; i++) {
                int type = in.readInt32(exception);
                if (type == 0x8ae5c97a) {
                    if (in.readInt32(exception) != 0x8f34b2f5) throw new RuntimeException("Unexpected business connection");
                    Connection item = new Connection(); item.readParams(in, exception); in.readInt32(exception);
                    if (id.equals(item.id)) found = item;
                } else if (TLRPC.Update.TLdeserialize(in, type, exception) == null) throw new RuntimeException("Unsupported update");
            }
            count = vectorCount(in, exception);
            for (int i = 0; i < count; i++) TLRPC.User.TLdeserialize(in, in.readInt32(exception), exception);
            count = vectorCount(in, exception);
            for (int i = 0; i < count; i++) TLRPC.Chat.TLdeserialize(in, in.readInt32(exception), exception);
            in.readInt32(exception); if (constructor == 0x725b04c3) in.readInt32(exception); in.readInt32(exception);
            if (found == null) throw new RuntimeException("Connection not found"); return found;
        }
    }
    private static int vectorCount(InputSerializedData in, boolean exception) {
        if (in.readInt32(exception) != 0x1cb5c415) throw new RuntimeException("Expected vector");
        int count = in.readInt32(exception); if (count < 0 || count > 10000) throw new RuntimeException("Invalid vector"); return count;
    }
}
