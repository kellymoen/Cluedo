package cluedo.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import cluedo.board.Board;
import cluedo.board.Location;
import cluedo.board.Room;
import cluedo.cards.Card;
import cluedo.actions.*;
import cluedo.tokens.CharacterToken;

/**
 * Main cluedo class that handles the game logic.
 */
public class Game {

	// system fields
	private UI ui;
	private Board board;

	// player fields
	private int numberPlayers;
	private Player[] players;

	// game fields
	private Deck deck;
	public static final String[] CHARACTERS = { "Miss Scarlett",
			"Colonel Mustard", "Mrs. White", "The Reverend Green",
			"Mrs. Peacock", "Professor Plum" };
	public static final String[] ROOMS = { "Kitchen", "Ballroom",
			"Conservatory", "Billiard Room", "Library", "Study", "Hall",
			"Lounge", "Dining Room" };
	public static final String[] WEAPONS = { "Candlestick", "Dagger",
			"Lead Pipe", "Revolver", "Rope", "Spanner" };

	/**
	 * Setup a new game of Cluedo.
	 */
	public Game() {
		// setup system
		board = new Board(WEAPONS, ROOMS);
		ui = new UI(board);

		// generate a new complete deck
		deck = new Deck(CHARACTERS, ROOMS, WEAPONS);

		// request the user for the number of users playing
		numberPlayers = ui.requestNumberPlayers();

		// create new players each with a unique character token
		players = setupPlayers(deck.getDeck());

		// generate the solution cards
		deck.generateSolution();

		// deal the remaining cards to the players
		deck.dealCards(players, numberPlayers);
	}

	/**
	 * Setup a new Game of Cluedo with no UI input. This is used exclusively for
	 * JUnit Testing.
	 */
	public Game(String test) {
		// setup system
		board = new Board(WEAPONS, ROOMS);
		ui = new UI(board);
	}

	/**
	 * Main game logic loop
	 */
	private void gameLoop() {
		// current player
		int playerIndex = -1;

		// loop until a player wins or the game exits
		int winner = 0;
		do {
			// move onto the next player
			playerIndex = (playerIndex + 1) % numberPlayers;
			Player player = players[playerIndex];

			// if the current player has been eliminated
			if (player.isEliminated()) {
				// skip their turn
				continue;
			}

			// check the current player is not the only player remaining
			if (playersRemaining() < 2) {
				// this player wins as they are the last remaining player
				winner = player.getId();
				continue;
			}

			// generate the dice roll
			int rollAmount = generateDiceRoll();

			// get the room the player is in (null for no room)
			Room playerRoom = board.roomIn(player.getToken());

			// request the player for an action they want to perform
			Action action = ui.requestPlayerAction(player, rollAmount,
					playerRoom);

			// if a valid move action was chosen
			if (action instanceof MoveAction) {
				// move the player and print the board result
				Location loc = ((MoveAction) action).getLocation();
				board.movePlayer(player.getToken(), loc);
				ui.printBoard();

				// update the room the player is in (may have moved out of or
				// changed room)
				playerRoom = board.roomIn(player.getToken());
			}

			// if an accusation action was chosen
			if (action instanceof AccusationAction) {
				// set winner to the result of the accusation
				// 1 means they won and 0 means the player was eliminated
				winner = performAccusation(player, (AccusationAction) action);
			}

			// if a secret passage action was chosen
			if (action instanceof SecretPassageAction) {
				// move the player via secret passage
				SecretPassageAction passageAction = (SecretPassageAction) action;
				board.moveTokenToRoom(player.getToken(),
						passageAction.getDestination());

				// update the room the player is in (may have moved through a
				// secret passage)
				playerRoom = board.roomIn(player.getToken());
			}

			// check to see if the player is in a room so we can offer the
			// player the option to make a suggestion, providing they have not
			// won the game or been eliminated from an accusation
			if (playerRoom != null && winner == 0 && !player.isEliminated()) {
				// player in a room so can make a suggestion
				SuggestionAction suggestion = ui.requestSuggestion(player,
						playerRoom);
				if (suggestion != null) {
					performSuggestion(player, suggestion, playerRoom);
				}
			}
		} while (winner == 0);

		// print the winner information and the soution
		ui.printWinner(players[winner - 1], deck.getSolution());
	}

	/**
	 * Returns the number of players in the game who have not been elimated.
	 * 
	 * @return The number of players remaining.
	 */
	private int playersRemaining() {
		int count = 0;
		for (int i = 0; i < numberPlayers; i++) {
			if (!players[i].isEliminated()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Generates a random number between 2 and 12 to simulate the rolling of two
	 * dice.
	 * 
	 * @return The resulting number from rolling the dice.
	 */
	public int generateDiceRoll() {
		return (int) (Math.random() * 11 + 2);
	}

	/**
	 * Given a player and accusation action, perform the accusation such that if
	 * the accusation was correct (the accusation cards match the solution
	 * cards) the player will win the game. If not, eliminate the player from
	 * the game.
	 * 
	 * @param player
	 *            The player performing the accusation.
	 * @param accusation
	 *            The accusation action made up of a character, room and weapon.
	 * @return Player ID if they won, 0 if they were eliminated.
	 */
	private int performAccusation(Player player, AccusationAction accusation) {
		// test the accusation
		if (testAccusation(accusation)) {
			// the accusation passed and the player wins
			return player.getId();
		} else {
			// player is eliminated due to bad accusation
			player.eliminate();
			ui.printPlayerElimination(player);
		}

		// no winner was decided
		return 0;
	}

	/**
	 * Test the given accusation matches the solution cards.
	 * 
	 * @param accusation
	 *            The accusation action made up of a character, room and weapon.
	 * @return True if it matched, false if not.
	 */
	private boolean testAccusation(AccusationAction accusation) {
		// check the accusation action contains the same cards as the solution
		return (deck.getSolution().contains(accusation.getCharacter())
				&& deck.getSolution().contains(accusation.getRoom()) && deck
				.getSolution().contains(accusation.getWeapon()));
	}

	/**
	 * Given a player and suggestion action, perform the suggestion. Iterate
	 * clockwise through the other players and if one of the other players has
	 * one of the suggestion cards they will refute the player suggestion.
	 * Showing one of the cards that matched at random.
	 * 
	 * @param player
	 *            The player performing the suggestion.
	 * @param suggestion
	 *            The suggestion action made up of a character, room and weapon.
	 * @param roomIn
	 *            The room the player is currently in.
	 */
	private void performSuggestion(Player player, SuggestionAction suggestion,
			Room roomIn) {
		Card refutedCard;

		// move the suggested character token to the room
		String character = suggestion.getCharacter().toString();
		board.moveTokenToRoom(board.getCharacterToken(character), roomIn);
		ui.printCharacterMove(character, roomIn.getName());

		// move the suggested weapon token to the room
		String weapon = suggestion.getWeapon().toString();
		board.moveTokenToRoom(board.getWeaponToken(weapon), roomIn);
		ui.printWeaponMove(weapon, roomIn.getName());

		// iterate through all the other players clockwise
		int i = player.getId() - 1;
		i = (i + 1) % numberPlayers;
		while (i != (player.getId() - 1)) {
			// compare the suggestion and current player hand
			refutedCard = checkForRefute(suggestion, players[i].getHand());

			// if a suggested card was refuted by the current player
			if (refutedCard != null) {
				// forget the card that was refuted
				player.refuteCard(refutedCard);

				// print information that a suggested card was refuted
				ui.printRefutedInfo(players[i], refutedCard);

				// stop iterating through the players now
				return;
			}

			// move onto the next player
			i = (i + 1) % numberPlayers;
		}

		// print information that no suggested card was refuted
		ui.printNonRefuted();
	}

	/**
	 * Check if the given player hand contains any of the suggested cards. If
	 * there is more than one matching cards, return one at random.
	 * 
	 * @param suggestion
	 *            The given suggestion action.
	 * @param hand
	 *            The player hand to check against.
	 * @return A card that intersects the hand and suggestion.
	 */
	private Card checkForRefute(SuggestionAction suggestion, HashSet<Card> hand) {
		// build a list of cards that intersect the suggestion and the hand
		List<Card> matches = new ArrayList<Card>();

		// check the character suggestion
		if (hand.contains(suggestion.getCharacter())) {
			matches.add(suggestion.getCharacter());
		}

		// check the room suggestion
		if (hand.contains(suggestion.getRoom())) {
			matches.add(suggestion.getRoom());
		}

		// check the weapon suggestion
		if (hand.contains(suggestion.getWeapon())) {
			matches.add(suggestion.getWeapon());
		}

		// if there was intersecting cards return one of them at random
		if (matches.size() > 0) {
			return matches.get((int) (Math.random() * matches.size()));
		}

		// no intersecting cards were found
		return null;
	}

	/**
	 * Setup the player objects for the game. Each player will have a unique
	 * character associated with it. Each player will be initialized with a list
	 * of possible cards that can be used for suggestions, refuted cards will be
	 * removed from this list.
	 * 
	 * @param possibleCards
	 *            All possible cards available in the game of cluedo.
	 * @return The new list of players.
	 */
	private Player[] setupPlayers(List<Card> possibleCards) {
		// create a list of players with size given by the number of players
		players = new Player[numberPlayers];

		// generate the available characters for token selection
		List<String> availableCharacters = new ArrayList<String>();
		availableCharacters.addAll(Arrays.asList(CHARACTERS));

		// for each player
		for (int i = 0; i < numberPlayers; i++) {
			// request the player to choose a character
			CharacterToken t = ui.requestCharacter(availableCharacters, i + 1);
			players[i] = new Player(t, i + 1);

			// give each player a copy of the deck for possible suggestion cards
			players[i].setNonRefutedCards(possibleCards);
		}
		return players;
	}

	/**
	 * Returns the game board. This is used exclusively for JUnit Testing.
	 * 
	 * @return Game board.
	 */
	public Board getBoard() {
		return board;
	}

	public static void main(String[] args) {
		// setup a new game
		Game game = new Game();

		// start the game
		game.gameLoop();
	}
}