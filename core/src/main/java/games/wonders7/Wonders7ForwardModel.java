package games.wonders7;

import games.wonders7.cards.Wonder7Board;
import games.wonders7.cards.Wonder7Card;
import core.AbstractGameState;
import core.CoreConstants;
import core.StandardForwardModel;
import core.actions.AbstractAction;
import core.components.Deck;
import games.wonders7.actions.*;
import utilities.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static games.wonders7.Wonders7Constants.createCardHash;

public class Wonders7ForwardModel extends StandardForwardModel {
// The rationale of the ForwardModel is that it contains the core game logic, while the GameState contains the underlying game data. 
// Usually this means that ForwardModel is stateless, and this is a good principle to adopt, but as ever there will always be exceptions.

    public void _setup(AbstractGameState state) {
        Wonders7GameState wgs = (Wonders7GameState) state;
        Wonders7GameParameters params = (Wonders7GameParameters) wgs.getGameParameters();

        // Sets game in Age 1
        wgs.currentAge = 1;
        wgs.direction = 1;

        // Then fills every player's hashmaps, so each player has 0 of each resource
        for (int i = 0; i < wgs.getNPlayers(); i++) { // For each
            for (Wonders7Constants.Resource type : Wonders7Constants.Resource.values()) {
                wgs.playerResources.get(i).put(type, 0);
            }
        }

        //System.out.println("THE GAME HAS STARTED");
        wgs.playerHands = new ArrayList<>();
        wgs.playedCards = new ArrayList<>();
        wgs.turnActions = new AbstractAction[wgs.getNPlayers()];
        wgs.playerWonderBoard = new Wonder7Board[wgs.getNPlayers()];
        wgs.ageDeck = new Deck<>("Age Deck", CoreConstants.VisibilityMode.MIXED_VISIBILITY);
        wgs.wonderBoardDeck = new Deck<>("Wonder Board Deck", CoreConstants.VisibilityMode.VISIBLE_TO_ALL);

        for (int i = 0; i < wgs.getNPlayers(); i++) {
            wgs.playerHands.add(new Deck<>("Player hand" + i, i, CoreConstants.VisibilityMode.VISIBLE_TO_OWNER));
            wgs.playedCards.add(new Deck<>("Played Cards", CoreConstants.VisibilityMode.VISIBLE_TO_ALL));
        }

        // Cards that have been discarded all players
        wgs.discardPile = new Deck<>("Discarded Cards", CoreConstants.VisibilityMode.HIDDEN_TO_ALL);

        // Shuffles wonder-boards
        createWonderDeck(wgs); // Adds Wonders into game
        wgs.wonderBoardDeck.shuffle(params.wonderShuffleSeed != -1 ? new Random(params.wonderShuffleSeed) : wgs.getRnd());

        // Gives each player wonder board and manufactured goods from the wonder
        for (int player = 0; player < wgs.getNPlayers(); player++) {
            wgs.setPlayerWonderBoard(player, wgs.wonderBoardDeck.draw());// Each player has one designated Wonder board

            // Players get their wonder board manufacturedGoods added to their resources
            Set<Wonders7Constants.Resource> keys = wgs.getPlayerWonderBoard(player).type.resourcesProduced.keySet(); // Gets all the resources the stage provides
            for (Wonders7Constants.Resource resource : keys) {  // Goes through all keys for each resource
                int stageValue = Math.toIntExact(wgs.getPlayerWonderBoard(player).type.resourcesProduced.get(resource)); // Number of resource the card provides
                int playerValue = wgs.getPlayerResources(player).get(resource); // Number of resource the player owns
                wgs.getPlayerResources(player).put(resource, stageValue + playerValue); // Adds the resources provided by the stage to the players resource count
            }
            // add coins
            wgs.getPlayerResources(player).put(Wonders7Constants.Resource.Coin, params.startingCoins);
        }

        ageSetup(wgs); // Shuffles deck and fills player hands, sets the turn owner
    }

    public void ageSetup(AbstractGameState state) {
        Wonders7GameState wgs = (Wonders7GameState) state;

        // Sets up the age
        createAgeDeck(wgs); // Fills Age1 deck with cards
        wgs.ageDeck.shuffle(wgs.cardRnd);
        //System.out.println("ALL THE CARDS IN THE GAME: "+wgs.AgeDeck.getSize());
        // Give each player their 7 cards, wonderBoard and the manufactured goods from the wonder-board
        for (int player = 0; player < wgs.getNPlayers(); player++) {
            for (int card = 0; card < ((Wonders7GameParameters) wgs.getGameParameters()).nWonderCardsPerPlayer; card++) {
                wgs.getPlayerHand(player).add(wgs.ageDeck.draw());
            }
        }

        // Player 0 starts
        wgs.setTurnOwner(0);
    }


    @Override
    public void _afterAction(AbstractGameState state, AbstractAction actionTaken) {
        Wonders7GameState wgs = (Wonders7GameState) state;
        int direction = wgs.getDirection();
        // EVERYBODY NOW PLAYS THEIR CARDS (ACTION ROUND)
        if (checkActionRound(wgs)) {
            for (int i = 0; i < wgs.getNPlayers(); i++) {
                wgs.setTurnOwner(i); // PLAYER i DOES THE ACTION THEY SELECTED, NOT ANOTHER PLAYERS ACTION
                wgs.getTurnAction(i).execute(wgs); // EXECUTE THE ACTION
                wgs.setTurnAction(i, null); // REMOVE EXECUTED ACTION
            }
            wgs.setTurnOwner(0);

            // PLAYER HANDS ARE NOW ROTATED AROUND EACH PLAYER
            Deck<Wonder7Card> temp = wgs.getPlayerHands().get(0);
            if (direction == 1) {
                for (int i = 0; i < wgs.getNPlayers(); i++) {
                    if (i == wgs.getNPlayers() - 1) {
                        wgs.getPlayerHands().set(i, temp);
                    } // makes sure the last player receives first players original hand
                    else {
                        wgs.getPlayerHands().set(i, wgs.getPlayerHands().get(i + 1));
                    } // Rotates hands clockwise
                }
            } else {
                temp = wgs.getPlayerHand((wgs.getNPlayers() - 1) % wgs.getNPlayers());
                for (int i = (wgs.getNPlayers() - 1) % wgs.getNPlayers(); i > -1; i--) {
                    if (i % wgs.getNPlayers() == 0) {
                        wgs.getPlayerHands().set(i, temp);
                    } // makes sure the last player receives first players original hand
                    else {
                        wgs.getPlayerHands().set(i, wgs.getPlayerHands().get(i - 1));
                    } // Rotates hands anticlockwise
                }
            }
            checkAgeEnd(wgs); // Check for Age end;

        } else {
            // we are still choosing cards for each player
            // this is covered by the code below
        }
        // We now check that all players have the same number of cards in hand
        int cardsExpected = wgs.getPlayerHand(0).getSize();
        for (int p = 1; p < wgs.getNPlayers(); p++)  {
            if (wgs.getPlayerHand(p).getSize() != cardsExpected) {
                throw new AssertionError("Player " + p + " has " + wgs.getPlayerHand(p).getSize() + " cards in hand, but player 0 has " + cardsExpected);
            }
        }

        // the next player is whoever still has something to choose
        if (wgs.isNotTerminal()) {
            boolean playerStillToChoose = true;
            for (int p = 0; p < wgs.getNPlayers(); p++) {
                if (wgs.getTurnAction(p) == null) {
                    endPlayerTurn(wgs, p);
                    playerStillToChoose = false;
                    break;
                }
            }
            if (playerStillToChoose) {
                throw new AssertionError("All players have chosen, so we should not be here");
            }
        }

    }

    protected boolean checkActionRound(AbstractGameState gameState) {
        Wonders7GameState wgs = (Wonders7GameState) gameState;
        for (int i = 0; i < wgs.getNPlayers(); i++) {
            if (wgs.turnActions[i] == null) return false;
        }
        return true;
    }

    @Override
    protected List<AbstractAction> _computeAvailableActions(AbstractGameState gameState) {
        Wonders7GameState wgs = (Wonders7GameState) gameState;
        int player = wgs.getCurrentPlayer();
        Deck<Wonder7Card> playerHand = wgs.getPlayerHand(player);
        Set<AbstractAction> actions = new LinkedHashSet<>();

        // If player has the prerequisite card/enough resources/the card is free/the player can pay for the resources to play the card
        for (Wonder7Card card : playerHand.getComponents()) { // Goes through each card in hand
            if (card.isAlreadyPlayed(player, wgs)) continue;

            if (card.isFree(player, wgs)) { // Checks if player has prerequisite
                actions.add((new PlayCard(player, card.cardName, true)));
            } else if (card.isPlayable(player, wgs)) {  // Meets the costs / can pay neighbours for resources
                actions.add(new PlayCard(player, card.cardName, false));
            }
        }

        // If next stage is playable or not
        if (wgs.getPlayerWonderBoard(player).isPlayable(wgs)) {
            for (int i = 0; i < playerHand.getSize(); i++) { // Goes through each card in hand
                actions.add(new BuildStage(player, playerHand.get(i).cardName));
            }
        }

        // All player can use special effect on wonder board
        if ((!wgs.getPlayerWonderBoard(player).effectUsed)) {
            for (int i = 0; i < playerHand.getSize(); i++) { // Goes through each card in hand
                actions.add(new SpecialEffect(player, playerHand.get(i).cardName));
            }
        }

        // All discard-able cards in player hand
        for (int i = 0; i < playerHand.getSize(); i++) {
            actions.add(new DiscardCard(playerHand.get(i).cardName, player));
        }

        return actions.stream().map(ChooseCard::new).collect(Collectors.toList());
    }

    protected void createWonderDeck(Wonders7GameState wgs) {
        // Create all the possible wonders a player could be assigned
        for (Wonder7Board.Wonder wonder : Wonder7Board.Wonder.values()) {
            if (wonder.constructionCosts == null) continue;  // Not implemented yet
            wgs.wonderBoardDeck.add(new Wonder7Board(wonder));
        }
    }

    protected void checkAgeEnd(AbstractGameState gameState) {
        Wonders7GameState wgs = (Wonders7GameState) gameState;
        if (wgs.getPlayerHand(wgs.getCurrentPlayer()).getSize() == 1) {  // If all players hands are empty

            for (int i = 0; i < wgs.getNPlayers(); i++) {
                if (wgs.getPlayerHand(i).getSize() > 0) {
                    wgs.getDiscardPile().add(wgs.getPlayerHand(i).get(0));
                    wgs.getPlayerHand(i).remove(0);
                }
            }

            endRound(wgs); // Ends the round,
            wgs.reverse(); // Turn Order reverses at end of Age

            // Resolves military conflicts
            for (int i = 0; i < wgs.getNPlayers(); i++) {
                int nextplayer = (i + 1) % wgs.getNPlayers();
                if (wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Shield) > wgs.getPlayerResources(nextplayer).get(Wonders7Constants.Resource.Shield)) { // IF PLAYER i WINS
                    wgs.getPlayerResources(i).put(Wonders7Constants.Resource.Victory, wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Victory) + (2 * wgs.currentAge - 1)); // 2N-1 POINTS FOR PLAYER i
                    wgs.getPlayerResources(nextplayer).put(Wonders7Constants.Resource.Victory, wgs.getPlayerResources(nextplayer).get(Wonders7Constants.Resource.Victory) - 1); // -1 FOR THE PLAYER i+1
                } else if (wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Shield) < wgs.getPlayerResources(nextplayer).get(Wonders7Constants.Resource.Shield)) { // IF PLAYER i+1 WINS
                    wgs.getPlayerResources(i).put(Wonders7Constants.Resource.Victory, wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Victory) - 1);// -1 POINT FOR THE PLAYER i
                    wgs.getPlayerResources(nextplayer).put(Wonders7Constants.Resource.Victory, wgs.getPlayerResources(nextplayer).get(Wonders7Constants.Resource.Victory) + (2 * wgs.currentAge - 1));// 2N-1 POINTS FOR PLAYER i+1
                }
            }

            wgs.getAgeDeck().clear();
            wgs.currentAge += 1; // Next age starts
            checkGameEnd(wgs); // Checks if the game has ended!
        }

    }

    protected void checkGameEnd(Wonders7GameState wgs) {
        if (wgs.currentAge == 4) {
            // Calculate victory points in order of:
            // treasury, scientific, commercial and finally guilds
            for (int i = 0; i < wgs.getNPlayers(); i++) {

                int vp = wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Victory);
                // Treasury
                vp += wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Coin) / 3;
                // Scientific
                vp += (int) Math.pow(wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Cog), 2);
                vp += (int) Math.pow(wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Compass), 2);
                vp += (int) Math.pow(wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Tablet), 2);
                // Sets of different science symbols
                vp += 7 * Math.min(Math.min(wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Cog), wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Compass)), wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Tablet));

                wgs.getPlayerResources(i).put(Wonders7Constants.Resource.Victory, vp);
            }

            int winner = 0;
            for (int i = 0; i < wgs.getNPlayers(); i++) {
                // If a player has more victory points
                if (wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Victory) > wgs.getPlayerResources(winner).get(Wonders7Constants.Resource.Victory)) {
                    wgs.setPlayerResult(CoreConstants.GameResult.LOSE_GAME, winner); // SETS PREVIOUS WINNER AS LOST
                    wgs.setPlayerResult(CoreConstants.GameResult.WIN_GAME, i); // SETS NEW WINNER AS PLAYER i
                    winner = i;
                }
                // In a tie, break with coins
                else if (wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Victory).equals(wgs.getPlayerResources(winner).get(Wonders7Constants.Resource.Victory))) {
                    if (wgs.getPlayerResources(i).get(Wonders7Constants.Resource.Coin) >= wgs.getPlayerResources(winner).get(Wonders7Constants.Resource.Coin)) {
                        wgs.setPlayerResult(CoreConstants.GameResult.LOSE_GAME, winner);
                        wgs.setPlayerResult(CoreConstants.GameResult.WIN_GAME, i);
                        winner = i;
                    } else {
                        wgs.setPlayerResult(CoreConstants.GameResult.LOSE_GAME, i);
                    }
                } else {
                    wgs.setPlayerResult(CoreConstants.GameResult.LOSE_GAME, i); // Sets this player as LOST
                }
            }

            wgs.setGameStatus(CoreConstants.GameResult.GAME_END);
            /*System.out.println("");
            System.out.println("!---------------------------------------- THE FINAL AGE HAS ENDED!!! ----------------------------------------!");
            System.out.println("");
            System.out.println("The winner is Player  " + winner +"!!!!"); */
        } else {
            /*System.out.println("");
            System.out.println("!---------------------------------------- AGE "+wgs.currentAge+" HAS NOW STARTED!!!!! ----------------------------------------!");
            System.out.println(""); */
            ageSetup(wgs);

            for (int player = 0; player < wgs.getNPlayers(); player++) {
                if (wgs.getPlayerWonderBoard(player).wonderStage > 2) {
                    Wonder7Board board = wgs.getPlayerWonderBoard(player);
                    switch (board.type) {
                        case TheLighthouseOfAlexandria:
                        case TheMausoleumOfHalicarnassus:
                        case TheHangingGardensOfBabylon:
                        case TheStatueOfZeusInOlympia:
                            wgs.getPlayerWonderBoard(player).effectUsed = false;
                        default:
                            break;
                    }
                }
            }
        }
    }

    protected void createAgeDeck(Wonders7GameState wgs) {
        // This method will create the deck for the current Era and
        // All the hashmaps containing different number of resources

        // TODO: Implement missing commercial cards (which will also involve updating the code to for buying resources)

        switch (wgs.currentAge) {
            // ALL THE CARDS IN DECK 1
            case 1:

                wgs.ageDeck.add(new Wonder7Card("Timber Yard", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood)));
                wgs.ageDeck.add(new Wonder7Card("Clay Pit", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay)));
                wgs.ageDeck.add(new Wonder7Card("Excavation", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone)));
                wgs.ageDeck.add(new Wonder7Card("Forest Cave", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood)));
                wgs.ageDeck.add(new Wonder7Card("Tree Farm", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood)));
                wgs.ageDeck.add(new Wonder7Card("Mine", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore)));

                for (int i = 0; i < 2; i++) {
                    // Raw Materials (Brown)
                    wgs.ageDeck.add(new Wonder7Card("Lumber Yard", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood)));
                    wgs.ageDeck.add(new Wonder7Card("Ore Vein", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore)));
                    wgs.ageDeck.add(new Wonder7Card("Clay Pool", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay)));
                    wgs.ageDeck.add(new Wonder7Card("Stone Pit", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone)));
                    // Manufactured Goods (Grey)
                    wgs.ageDeck.add(new Wonder7Card("Loom", Wonder7Card.Type.ManufacturedGoods, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Textile)));
                    wgs.ageDeck.add(new Wonder7Card("GlassWorks", Wonder7Card.Type.ManufacturedGoods, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Glass)));
                    wgs.ageDeck.add(new Wonder7Card("Press", Wonder7Card.Type.ManufacturedGoods, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Papyrus)));
                    // Civilian Structures (Blue)
                    wgs.ageDeck.add(new Wonder7Card("Altar", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory)));
                    wgs.ageDeck.add(new Wonder7Card("Theatre", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory)));
                    wgs.ageDeck.add(new Wonder7Card("Baths", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory)));
                    wgs.ageDeck.add(new Wonder7Card("Pawnshop", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory)));
                    // Scientific Structures (Green)
                    wgs.ageDeck.add(new Wonder7Card("Apothecary", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Textile), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Compass)));
                    wgs.ageDeck.add(new Wonder7Card("Workshop", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Glass), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Cog)));
                    wgs.ageDeck.add(new Wonder7Card("Scriptorium", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Papyrus), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Tablet)));
                    // Commercial Structures (Yellow)
                    // MilitaryStructures (Red)
                    wgs.ageDeck.add(new Wonder7Card("Stockade", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Shield)));
                    wgs.ageDeck.add(new Wonder7Card("Barracks", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Shield)));
                    wgs.ageDeck.add(new Wonder7Card("Guard Tower", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Shield)));

                }
                for (int i = 0; i < 3; i++) {
                    // Commercial Structures (Yellow)
                    wgs.ageDeck.add(new Wonder7Card("Tavern", Wonder7Card.Type.CommercialStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin, Wonders7Constants.Resource.Coin, Wonders7Constants.Resource.Coin, Wonders7Constants.Resource.Coin, Wonders7Constants.Resource.Coin)));
                }
                break;

            // ALL THE CARDS IN DECK 2
            case 2:

                // Extra cards for 6 players
                wgs.ageDeck.add(new Wonder7Card("ForumG", Wonder7Card.Type.CommercialStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Glass)));
                wgs.ageDeck.add(new Wonder7Card("ForumT", Wonder7Card.Type.CommercialStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Textile)));
                wgs.ageDeck.add(new Wonder7Card("ForumP", Wonder7Card.Type.CommercialStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Papyrus)));


                for (int i = 0; i < 2; i++) {
                    // Raw Materials (Brown)
                    wgs.ageDeck.add(new Wonder7Card("Sawmill", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Wood)));
                    wgs.ageDeck.add(new Wonder7Card("Foundry", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore)));
                    wgs.ageDeck.add(new Wonder7Card("Brickyard", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay)));
                    wgs.ageDeck.add(new Wonder7Card("Quarry", Wonder7Card.Type.RawMaterials, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Coin), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone)));
                    // Manufactured Goods (Grey)
                    wgs.ageDeck.add(new Wonder7Card("Loom", Wonder7Card.Type.ManufacturedGoods, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Textile)));
                    wgs.ageDeck.add(new Wonder7Card("GlassWorks", Wonder7Card.Type.ManufacturedGoods, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Glass)));
                    wgs.ageDeck.add(new Wonder7Card("Press", Wonder7Card.Type.ManufacturedGoods, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Papyrus)));
                    // Civilian Structures (Blue)
                    wgs.ageDeck.add(new Wonder7Card("Temple", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Glass), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory), "Altar"));
                    wgs.ageDeck.add(new Wonder7Card("Courthouse", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Textile), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory), "Scriptorium"));
                    wgs.ageDeck.add(new Wonder7Card("Statue", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Wood), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory, Wonders7Constants.Resource.Victory), "Theatre"));
                    // Scientific Structures (Green)
                    wgs.ageDeck.add(new Wonder7Card("Library", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Textile), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Tablet), "Scriptorium"));
                    wgs.ageDeck.add(new Wonder7Card("Laboratory", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Papyrus), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Cog), "Workshop"));
                    wgs.ageDeck.add(new Wonder7Card("Dispensary", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Glass), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Compass), "Apothecary"));
                    wgs.ageDeck.add(new Wonder7Card("School", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Papyrus), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Tablet)));
                    // Commercial Structures (Yellow)
                    // MilitaryStructures (Red)
                    wgs.ageDeck.add(new Wonder7Card("Stables", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Ore), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Shield, Wonders7Constants.Resource.Shield), "Apothecary"));
                    wgs.ageDeck.add(new Wonder7Card("Archery Range", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Ore), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Shield, Wonders7Constants.Resource.Shield), "Workshop"));
                    wgs.ageDeck.add(new Wonder7Card("Walls", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Shield, Wonders7Constants.Resource.Shield)));
                }
                for (int i = 0; i < 3; i++) {
                    // MilitaryStructures (Red)
                    wgs.ageDeck.add(new Wonder7Card("Training Ground", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Wood), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Shield, Wonders7Constants.Resource.Shield)));
                }
                break;

            // ALL THE CARDS IN DECK 3
            case 3:

                //Extra cards for 6 players
                wgs.ageDeck.add(new Wonder7Card("Gardens", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Wood), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 5)), "Statue"));
                wgs.ageDeck.add(new Wonder7Card("Senate", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Ore), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 6)), "Library"));
                wgs.ageDeck.add(new Wonder7Card("Town Hall", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Glass), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 6))));
                wgs.ageDeck.add(new Wonder7Card("Pantheon", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Glass, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Textile), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 7)), "Temple"));
                wgs.ageDeck.add(new Wonder7Card("University", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Glass), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Tablet), "Library"));
                wgs.ageDeck.add(new Wonder7Card("Lodge", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Textile), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Compass), "Dispensary"));
                wgs.ageDeck.add(new Wonder7Card("Study", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Textile), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Cog), "School"));
                wgs.ageDeck.add(new Wonder7Card("Siege Workshop", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Wood), createCardHash(new Pair<>(Wonders7Constants.Resource.Shield, 3)), "Laboratory"));
                wgs.ageDeck.add(new Wonder7Card("Arsenal", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Textile), createCardHash(new Pair<>(Wonders7Constants.Resource.Shield, 3))));
                wgs.ageDeck.add(new Wonder7Card("Fortification", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Stone), createCardHash(new Pair<>(Wonders7Constants.Resource.Shield, 3)), "Walls"));
                wgs.ageDeck.add(new Wonder7Card("Circus", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Ore), createCardHash(new Pair<>(Wonders7Constants.Resource.Shield, 3)), "Training Ground"));

                for (int i = 0; i < 2; i++) {
                    // Civilian Structures (Blue)
                    wgs.ageDeck.add(new Wonder7Card("Gardens", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Wood), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 5)), "Statue"));
                    wgs.ageDeck.add(new Wonder7Card("Senate", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Ore), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 6)), "Library"));
                    wgs.ageDeck.add(new Wonder7Card("Pantheon", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Glass, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Textile), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 7)), "Temple"));
                    wgs.ageDeck.add(new Wonder7Card("Palace", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Glass, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Textile), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 8))));
                    // Scientific Structures (Green)
                    wgs.ageDeck.add(new Wonder7Card("University", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Glass), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Tablet), "Library"));
                    wgs.ageDeck.add(new Wonder7Card("Observatory", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Glass, Wonders7Constants.Resource.Textile), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Cog), "Laboratory"));
                    wgs.ageDeck.add(new Wonder7Card("Lodge", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Textile), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Compass), "Dispensary"));
                    wgs.ageDeck.add(new Wonder7Card("Study", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Papyrus, Wonders7Constants.Resource.Textile), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Cog), "School"));
                    wgs.ageDeck.add(new Wonder7Card("Academy", Wonder7Card.Type.ScientificStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Glass), Wonders7Constants.createCardHash(Wonders7Constants.Resource.Compass), "School"));
                    // MilitaryStructures (Red)
                    wgs.ageDeck.add(new Wonder7Card("Siege Workshop", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Clay, Wonders7Constants.Resource.Wood), createCardHash(new Pair<>(Wonders7Constants.Resource.Shield, 3)), "Laboratory"));
                    wgs.ageDeck.add(new Wonder7Card("Fortification", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Stone), createCardHash(new Pair<>(Wonders7Constants.Resource.Shield, 3)), "Walls"));

                }
                for (int i = 0; i < 3; i++) {
                    // Civilian Structures (Blue)
                    wgs.ageDeck.add(new Wonder7Card("Town Hall", Wonder7Card.Type.CivilianStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Glass), createCardHash(new Pair<>(Wonders7Constants.Resource.Victory, 6))));

                    // MilitaryStructures (Red)
                    wgs.ageDeck.add(new Wonder7Card("Arsenal", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Wood, Wonders7Constants.Resource.Ore, Wonders7Constants.Resource.Textile), createCardHash(new Pair<>(Wonders7Constants.Resource.Shield, 3))));
                    wgs.ageDeck.add(new Wonder7Card("Circus", Wonder7Card.Type.MilitaryStructures, Wonders7Constants.createCardHash(Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Stone, Wonders7Constants.Resource.Ore), createCardHash(new Pair<>(Wonders7Constants.Resource.Shield, 3)), "Training Ground"));
                }
        }
    }
}

