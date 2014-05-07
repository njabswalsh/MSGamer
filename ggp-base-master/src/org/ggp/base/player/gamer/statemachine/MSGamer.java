package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class MSGamer extends StateMachineGamer {

	private static final long serverTimeBuffer = 1000;
	private static final boolean debug = false;
	private static final boolean useDepthLimit = true;
	private static final int MonteCarloCount = 4;
	private static final int depthIncrement = 1;
	private static final double explorationConstant = 40;
	private int depthLimit = 200;
	private long timeout = 0;
	private List<Role> players;
	private int playerNumber;
	private Role role;
	private Random r = new Random();
	private GameNode gameTree;
	StateMachine stateMachine;
	@Override
	public StateMachine getInitialStateMachine()
	{
		System.out.println("Initialized");
		return new ProverStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException
	{
		stateMachine = getStateMachine();
		players = stateMachine.getRoles();
		role = getRole();
		playerNumber = findPlayerNum();
		if(playerNumber < 0) System.out.println("We've got a problem: role does not exist.");
		MachineState initialState = stateMachine.getInitialState();
		boolean isMaxNode = false;
		if(stateMachine.getLegalMoves(initialState, role).size() > 1) isMaxNode = true;
		gameTree = new GameNode(initialState, null, null, isMaxNode);
	}

	private int findPlayerNum()
	{
		for(int i = 0; i < players.size(); i++)
		{
			if(role.equals(players.get(i))) return i;
		}
		return -1;
	}

	private boolean timedOut()
	{
		boolean returnVal = System.currentTimeMillis() + serverTimeBuffer > timeout && !debug;
		if(returnVal) System.out.println("Timed out");
		return returnVal;
	}

	private GameNode select(GameNode node)
	{
		int level = 0;
		while(true)
		{
			if(node.visits == 0 || stateMachine.isTerminal(node.state)) return node;

			for (int i = 0; i < node.children.size(); i++)
			{
				if(node.children.get(i).visits == 0)
				{
					return node.children.get(i);
				}
			}
			double score = 0;
			GameNode result = node;
			for(int i = 0; i < node.children.size(); i++)
			{
				int playerSelect = playerNumber;
				if(!node.isMaxNode) playerSelect ^= 1; //TODO fix
				double newscore = selectfn(node.children.get(i), playerSelect);
				if(newscore > score)
				{
					score = newscore;
					result = node.children.get(i);
				}
			}
			node = result;
			level++;
			if(level % 500 == 0)
			{
				System.out.println("Select level: " + level);
			}
		}
	}

	private double selectfn(GameNode node, int playerNumber) {
		return node.utilities.get(playerNumber) + explorationConstant * Math.sqrt(2 * Math.log(node.parent.visits) / node.visits);
	}

	private boolean expand(GameNode node) throws MoveDefinitionException, TransitionDefinitionException
	{
		if(stateMachine.isTerminal(node.state)) return false;
		if(players.size() > 1) return expandTwoPlayerGame(node);
		else return expandOnePlayerGame(node);
	}

	private boolean expandOnePlayerGame(GameNode node) throws TransitionDefinitionException, MoveDefinitionException {
		List<Move> playerActions = stateMachine.getLegalMoves(node.state, role);
		if(playerActions.size() > 1)
		{
			for(int i = 0; i < playerActions.size(); i++)
			{
				List<Move> moves = new ArrayList<Move>();
				moves.add(playerActions.get(i));
				MachineState newstate = stateMachine.getNextState(node.state, moves);
				boolean isMaxNode = true;
				GameNode newNode = new GameNode(newstate, node, playerActions.get(i), isMaxNode);
				node.children.add(newNode);
			}
		}
		return true;
	}

	private boolean expandTwoPlayerGame(GameNode node)
			throws MoveDefinitionException, TransitionDefinitionException {
		Role opponent = players.get(playerNumber ^ 1);
		List<Move> playerActions = stateMachine.getLegalMoves(node.state, role);
		if(playerActions.size() > 1)
		{
			for(int i = 0; i < playerActions.size(); i++)
			{
				List<Move> moves = new ArrayList<Move>();
				for(int j = 0; j < players.size(); j++)
				{
					if(j == playerNumber)
					{
						moves.add(playerActions.get(i));
					}
					else
					{
						moves.add(stateMachine.getRandomMove(node.state, opponent));
					}
				}
				MachineState newstate = stateMachine.getNextState(node.state, moves);
				boolean isMaxNode = true;
				GameNode newNode = new GameNode(newstate, node, playerActions.get(i), isMaxNode);
				node.children.add(newNode);
			}
		}
		else
		{
			List<Move> opponentActions = stateMachine.getLegalMoves(node.state, opponent); //TODO Fix terrible code
			Move playerAction = stateMachine.getRandomMove(node.state, role);
			for(int i = 0; i < opponentActions.size(); i++)
			{
				List<Move> moves = new ArrayList<Move>();
				for(int j = 0; j < players.size(); j++)
				{
					if(j == playerNumber)
					{
						moves.add(playerAction);
					}
					else
					{
						moves.add(opponentActions.get(i));
					}
				}
				moves.add(opponentActions.get(i));
				MachineState newstate = stateMachine.getNextState(node.state, moves);
				boolean isMaxNode = false;
				GameNode newNode = new GameNode(newstate, node, playerAction, isMaxNode);
				node.children.add(newNode);
			}
		}
		return true;
	}

	private void backpropagate(GameNode node, List<Integer> scores)
	{
		for(int i = 0; i < players.size(); i++)
		{
			double newUtility = (node.utilities.get(i) * node.visits + scores.get(i)) / (node.visits + 1);
			node.utilities.set(i, newUtility);
		}
		node.visits++;
		if(node.parent != null) backpropagate(node.parent, scores);
	}

	@SuppressWarnings("unused")
	private int mobilityEval(Role role, MachineState state) throws MoveDefinitionException
	{
		List<Move> legalMoves = stateMachine.getLegalMoves(state, role);
		return legalMoves.size();
	}

	@SuppressWarnings("unused")
	private int focusEval(Role role, MachineState state) throws MoveDefinitionException
	{
		List<Move> legalMoves = stateMachine.getLegalMoves(state, role);
		return 100 - legalMoves.size();
	}

//	private int monteCarlo(Role role, MachineState state, int count) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
//	{
//		int total = 0;
//		for(int i = 0; i < count; i++)
//		{
//			total = total + depthCharge(role, state);
//		}
//		return (total / count );
//	}

	private List<Integer> depthCharge(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		int depth = 0;
		while (!stateMachine.isTerminal(state) && depth < depthLimit) {
            state = stateMachine.getNextStateDestructively(state, stateMachine.getRandomJointMove(state));
		}
		if(depth == depthLimit) System.out.println("Depth limit reached");
		List<Integer> scores = new ArrayList<Integer>();
		for(int i = 0; i < players.size(); i++)
		{
			scores.add(stateMachine.getGoal(state, players.get(i)));
		}
		return scores;
	}

	@SuppressWarnings("unused")
	private int goalProximityEval(Role role, MachineState state) throws GoalDefinitionException
	{
		return stateMachine.getGoal(state, role);
	}

	private int evalFunction(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException
	{
		//return monteCarlo(role, state, MonteCarloCount) - 1;
		//return focusEval(players.get(playerNumber ^ 1), state);
		return goalProximityEval(role, state);
	}

	private int minscore(Role role, Move action, MachineState state, int alpha, int beta, int level) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException
	{
		List<Move> move = new ArrayList<Move>(players.size());
		for(int i = 0; i < players.size(); i ++)
		{
			move.add(null);
		}
		return minscore(role, action, state, alpha, beta, move , 0, level);
	}

	private int minscore(Role role, Move action, MachineState state, int alpha, int beta, List<Move> move, int playerIndex, int level) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException
	{
		Role opponent = players.get(playerIndex);
		List<Move> possibleActions;
		if(opponent.equals(role))
		{
			possibleActions = new ArrayList<Move>();
			possibleActions.add(action);
		}
		else
		{
			possibleActions = stateMachine.getLegalMoves(state, opponent);
		}
		for (int i = 0; i < possibleActions.size(); i++)
		{
			if(timedOut())
			{
				return beta;
			}
			move.set(playerIndex, possibleActions.get(i));
			if(playerIndex == players.size() - 1)
			{
				MachineState newstate = stateMachine.getNextState(state, move);

				int result = maxscore(role, newstate, alpha, beta, level + 1);
				beta = Math.min(beta, result);
				if (beta <= alpha) { return alpha; }
			}
			else
			{
				int result = minscore(role, action, state, alpha, beta, move, playerIndex + 1, level);
				beta = Math.min(beta, result);
				if (beta <= alpha) { return alpha; }
			}
		}
		return beta;
	}

	private int maxscore(Role role, MachineState state, int alpha, int beta, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
	{
		if (stateMachine.isTerminal(state)) {
			return stateMachine.getGoal(state, role);
		}
		if (level >= depthLimit && useDepthLimit) return evalFunction(role, state);
		List<Move> legalMoves = stateMachine.getLegalMoves(state, role);
		for (int i = 0; i < legalMoves.size(); i++) {
			if(timedOut())
			{
				return alpha;
			}
			int result = minscore(role, legalMoves.get(i), state, alpha, beta, level);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) { return beta; }
		}
		return alpha;

	}

	public Move getBestMove(Role role, MachineState currentState, StateMachine stateMachine) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException
	{
		//TODO maintain old game tree
		//TODO Add threading
		boolean isMaxNode = false;
		if(stateMachine.getLegalMoves(currentState, role).size() > 1) isMaxNode = true;
		gameTree = new GameNode(currentState, null, null, isMaxNode);
		int nodeCount = 0;
		while(true)
		{
			if(nodeCount % 1000 == 0) System.out.println("Nodecount: " + nodeCount + " Free memory: " + Runtime.getRuntime().freeMemory());
			GameNode node = select(gameTree);
			boolean nonTerminal = expand(node);
			List<Integer> scores = depthCharge(role, node.state);
			backpropagate(node, scores);
			nodeCount++;
			if(timedOut()) break;
		}

		double bestScore = 0;
		Move bestMove = stateMachine.getRandomMove(currentState, role);
		for(int i = 0; i < gameTree.children.size(); i++)
		{
			double score = gameTree.children.get(i).utilities.get(playerNumber);
			if(score > bestScore)
			{
				bestScore = score;
				bestMove = gameTree.children.get(i).move;
			}
		}
		return bestMove;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		MachineState currentState = getCurrentState();
		this.timeout = timeout;
		return getBestMove(role, currentState, stateMachine);
	}

	@Override
	public void stateMachineStop() {
		cleanup();
	}

	@Override
	public void stateMachineAbort() {
		cleanup();
	}

	private void cleanup()
	{
		timeout = 0;
		players = null;
		playerNumber = 0;
		role = null;
		stateMachine = null;
		gameTree = null;
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {

	}

	@Override
	public String getName() {
		return "MSGamer";
	}

}
