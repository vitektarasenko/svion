package omsu.svion.game.handler.impl;

import omsu.svion.game.Game;
import omsu.svion.game.Player;
import omsu.svion.game.handler.GameMessageHandler;
import omsu.svion.game.states.ChoosingCategory;
import omsu.svion.game.states.Playing;
import omsu.svion.game.worker.PlayerConnectorOrGameRemover;
import omsu.svion.messages.*;
import omsu.svion.model.json.converter.PlayerConverter;
import omsu.svion.questions.Cost;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;

/**
 * Created by victor on 11.04.14.
 */
@Service("playerAdded")
public class PlayerAddedHandler implements GameMessageHandler {
    @Autowired
    private TaskExecutor taskExecutor;
    @Autowired
    private PlayerConnectorOrGameRemover playerConnectorOrGameRemover;
    private static final Logger logger = Logger.getLogger(PlayerAddedHandler.class);
    @Autowired
    private ObjectMapper objectMapper;
    public void handle(AbstractMessage message, Game game) {
        logger.debug("1");
        logger.debug(game.isReadyToStart());
        if (game.isReadyToStart()) {
            logger.debug("game started!");
            game.setState(ChoosingCategory.class);
        }
        logger.debug("2");
        PlayerConverter playerConverter = new PlayerConverter();
        if (game.getState().equals(ChoosingCategory.class)) {
            int answeringPlayer = Math.abs(new SecureRandom().nextInt()) % game.getPlayers().values().size();

            logger.debug("3");
            Player playerToAnswer= null;
            logger.debug("4");
            int i= 0;
            logger.debug("5");
            for (Player player : game.getPlayers().values()) {
                    if (i == answeringPlayer) {
                       playerToAnswer = player;
                    }
                ++i;
            }
            logger.debug("5");
            logger.debug("player to answer "+playerToAnswer);
            ChooseThemeAndCostRequestMessage mes = new ChooseThemeAndCostRequestMessage(playerConverter.convert(new ArrayList<Player>(game.getPlayers().values())), game.getAvailableCostsAndThemes().get(game.getTourNumber() - 1), false, game.getTourNumber(), game.getCurrentQuestionNumber(),playerToAnswer.getEmail(),null);
            logger.debug("6");
            ChooseThemeAndCostRequestMessage messageForAnswering = new ChooseThemeAndCostRequestMessage(playerConverter.convert(new ArrayList<Player>(game.getPlayers().values())), game.getAvailableCostsAndThemes().get(game.getTourNumber() - 1), true, game.getTourNumber(), game.getCurrentQuestionNumber(),playerToAnswer.getEmail(),null);
            logger.debug("6");
             i= 0;
            for (Player player : game.getPlayers().values()) {
                if (player.getState() != Player.State.ONLINE) {
                    logger.debug("skipped player" + player);
                    continue;
                }
                try {
                    if (i == answeringPlayer) {
                        player.getWebSocketSession().sendMessage(new TextMessage(objectMapper.writeValueAsString(messageForAnswering)));
                        game.setPreviousAnsweredPlayer(player);
                    } else {
                        player.getWebSocketSession().sendMessage(new TextMessage(objectMapper.writeValueAsString(mes)));
                    }
                } catch (IOException e) {
                    UserOccasionallyDisconnected userOccasionallyDisconnected = new UserOccasionallyDisconnected();
                    userOccasionallyDisconnected.setSession(message.getSession());
                    playerConnectorOrGameRemover.handle(userOccasionallyDisconnected);
                }
                ++i;
            }
            logger.debug("7");
            final Game game1 = game;
            final WebSocketSession webSocketSession = message.getSession();
            Runnable costAndThemeChosenChecker = new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (game1.getCostAndThemeChosenChecker() == this) {
                        logger.debug("woke up, theme and cost was not chosen");
                        ThemeAndCostNotChosenMessage themeAndCostNotChosenMessage = new ThemeAndCostNotChosenMessage();
                        themeAndCostNotChosenMessage.setSession(webSocketSession);
                        game1.handleMessage(themeAndCostNotChosenMessage);
                    }
                }
            };
            game.setCostAndThemeChosenChecker(costAndThemeChosenChecker);
            taskExecutor.execute(costAndThemeChosenChecker);
            }
            else {
            logger.debug("sending game update message, still game not started");
            GameStateUpdateMessage gameStateUpdateMessage = new GameStateUpdateMessage(playerConverter.convert(new ArrayList<Player>(game.getPlayers().values())),game.getState());
            logger.debug("a "+game.getPlayers().values().size());
            logger.debug("b");
            for (Player player : game.getPlayers().values()) {
                logger.debug("lol1");
                logger.debug("lol2");
                if (player.getState() != Player.State.ONLINE) {
                    logger.debug("skipped player" + player);
                    continue;
                }
                try {
                    player.getWebSocketSession().sendMessage(new TextMessage(objectMapper.writeValueAsString(gameStateUpdateMessage)));
                    logger.debug("lol3");

                } catch (IOException e) {
                    logger.error(e);
                    UserOccasionallyDisconnected userOccasionallyDisconnected = new UserOccasionallyDisconnected();
                    userOccasionallyDisconnected.setSession(message.getSession());
                    playerConnectorOrGameRemover.handle(userOccasionallyDisconnected);
                }
            }
            logger.debug("sending game update message, still game not started 1");
            }

        }
}
