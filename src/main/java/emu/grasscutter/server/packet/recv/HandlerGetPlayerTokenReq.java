package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import emu.grasscutter.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.server.event.game.PlayerCreationEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.game.GameSessionManager;
import emu.grasscutter.server.packet.send.PacketGetPlayerTokenRsp;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.helpers.ByteHelper;
import java.nio.ByteBuffer;
import java.security.Signature;
import javax.crypto.Cipher;

@Opcodes(PacketOpcodes.GetPlayerTokenReq)
public class HandlerGetPlayerTokenReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Read through the generated proto, which lines up with what the client puts on the wire:
        // account_uid = 2, account_token = 6, key_id = 588 and client_rand_key = 932.
        GetPlayerTokenReq req;
        try {
            req = GetPlayerTokenReq.parseFrom(payload);
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .warn("Could not read GetPlayerTokenReq: {}", exception.getMessage());
            session.close();
            return;
        }

        var accountId = req.getAccountUid();
        var accountToken = req.getAccountToken();
        var clientRandKey = req.getClientRandKey();
        var keyId = req.getKeyId();

        var account = DispatchUtils.authenticate(accountId, accountToken);

        if (account == null && !DebugConstants.ACCEPT_CLIENT_TOKEN) {
            session.close();
            return;
        } else if (account == null && DebugConstants.ACCEPT_CLIENT_TOKEN) {
            account = DispatchUtils.getAccountById(accountId);
            if (account == null) {
                session.close();
                return;
            }
        }

        session.setAccount(account);

        boolean kicked = false;
        var exists = Grasscutter.getGameServer().getPlayerByAccountId(accountId);
        if (exists != null) {
            var existsSession = exists.getSession();
            if (existsSession != session) {
                exists.onLogout();
                existsSession.close();
                Grasscutter.getLogger()
                    .warn("Player {} was kicked due to duplicated login", account.getUsername());
                kicked = true;
            }
        }

        if (!kicked) {

            if (ACCOUNT.maxPlayer > -1
                && Grasscutter.getGameServer().getPlayers().size() >= ACCOUNT.maxPlayer) {
                session.close();
                return;
            }
        }

        var event = new PlayerCreationEvent(session, Player.class);
        event.call();

        // The account's player document - and, for a first login, its creation - is a database
        // round-trip. This handler runs on the logic thread that every session shares, so a slow
        // lookup stalls every other player: look the player up on the database executor instead and
        // only hop back to the logic thread once the character exists. Session state and packet
        // writes stay single-threaded.
        final var loginAccount = account;
        DatabaseHelper.getEventExecutor()
                .submit(
                        () -> {
                            Player loaded;
                            try {
                                loaded =
                                        DatabaseHelper.getPlayerByAccount(
                                                loginAccount, event.getPlayerClass());

                                if (loaded == null) {
                                    var nextPlayerUid =
                                            DatabaseHelper.getNextPlayerId(
                                                    loginAccount.getReservedPlayerUid());

                                    loaded =
                                            event.getPlayerClass()
                                                    .getDeclaredConstructor(GameSession.class)
                                                    .newInstance(session);

                                    DatabaseHelper.generatePlayerUid(loaded, nextPlayerUid);
                                }
                            } catch (Throwable exception) {
                                Grasscutter.getLogger()
                                        .error(
                                                "Could not load the player of account {}: {}",
                                                accountId,
                                                exception.getMessage(),
                                                exception);
                                GameSessionManager.getLogicThread().execute(session::close);
                                return;
                            }

                            var loadedPlayer = loaded;
                            GameSessionManager.getLogicThread()
                                    .execute(
                                            () ->
                                                    finishTokenRequest(
                                                            session, loadedPlayer, keyId, clientRandKey));
                        });
    }

    /**
     * Answers a GetPlayerTokenReq once the account's player is available. Runs on the logic thread,
     * after {@link #handle} has loaded the player on the database executor.
     *
     * @param session The session that requested the token.
     * @param player The loaded, or freshly created, player of the account.
     * @param keyId The RSA key id offered by the client, or 0 for an unencrypted exchange.
     * @param clientRandKey The client's base64 encrypted seed, empty when {@code keyId} is 0.
     */
    private static void finishTokenRequest(
            GameSession session, Player player, int keyId, String clientRandKey) {
        session.setPlayer(player);

        if (session.getAccount().isBanned()) {
            session.setState(SessionState.ACCOUNT_BANNED);
            session.send(
                new PacketGetPlayerTokenRsp(
                    session, 21, "FORBID_CHEATING_PLUGINS", session.getAccount().getBanEndTime()));
            return;
        }

        player.loadFromDatabase();

        if (Grasscutter.getConfig().server.game.useXorEncryption) {
            session.setState(SessionState.WAITING_FOR_LOGIN);

            if (keyId > 0) {
                var encryptSeed = session.getEncryptSeed();
                try {
                    var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    cipher.init(Cipher.DECRYPT_MODE, Crypto.CUR_SIGNING_KEY);

                    var clientSeedEncrypted = Utils.base64Decode(clientRandKey);
                    var clientSeed = ByteBuffer.wrap(cipher.doFinal(clientSeedEncrypted)).getLong();

                    var combinedSeed = encryptSeed ^ clientSeed;
                    var seedBytes = ByteBuffer.wrap(new byte[8]).putLong(combinedSeed).array();

                    cipher.init(Cipher.ENCRYPT_MODE, Crypto.EncryptionKeys.get(keyId));
                    var seedEncrypted = cipher.doFinal(seedBytes);

                    var privateSignature = Signature.getInstance("SHA256withRSA");
                    privateSignature.initSign(Crypto.CUR_SIGNING_KEY);
                    privateSignature.update(seedBytes);

                    // Exactly ONE response per request. Sending several candidates back to back was
                    // meant to test the field map faster, but a real server answers once, and a
                    // duplicate response to a finished request is its own reason for a client to
                    // give up - which would mask the very thing the candidates were testing. The
                    // client reopens this exchange every 30-60 seconds by itself, so one candidate
                    // per request still covers the whole rotation in a few minutes.
                    var rsp = new PacketGetPlayerTokenRsp(
                            session,
                            Utils.base64Encode(seedEncrypted),
                            Utils.base64Encode(privateSignature.sign()),
                            keyId);
                    session.send(rsp);
                    switchWireKey(session);
                } catch (Exception ignored) {

                    Grasscutter.getLogger().error("GetPlayerTokenReq RSA failed (key_id={}, clientRandKey len={}): {}",
                        keyId,
                        clientRandKey.isEmpty() ? 0 : Utils.base64Decode(clientRandKey).length,
                        ignored.getClass().getSimpleName() + ": " + ignored.getMessage());
                    var clientBytes = Utils.base64Decode(clientRandKey);
                    var seed = ByteHelper.longToBytes(encryptSeed);
                    Crypto.xor(clientBytes, seed);

                    var base64str = Utils.base64Encode(clientBytes);
                    session.send(new PacketGetPlayerTokenRsp(session, base64str, "bm90aGluZyBoZXJl", keyId));
                    switchWireKey(session);
                }
            } else {
                session.send(new PacketGetPlayerTokenRsp(session, keyId));
            }
        } else {
            session.setState(SessionState.WAITING_FOR_LOGIN);
            session.send(new PacketGetPlayerTokenRsp(session, keyId));
        }
    }

    /**
     * Moves the connection onto the negotiated session key.
     *
     * <p>This used to be skipped on 7.x, because the client could not read our response and so never
     * moved with us, and switching alone would have turned its pings - the only signal still coming
     * back - into noise. That trade is gone: the session now works out which key a frame arrived
     * under instead of assuming, so it follows the client either way and nothing is lost by
     * switching. Keeping the skip would be actively harmful now, because the moment the field map is
     * right the client switches and the server has to be there with it.
     */
    private static void switchWireKey(GameSession session) {
        session.setUseSecretKey(true);
    }
}
