package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.Pair;
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

	private static final long serverTimeBuffer = 2000;
	private static final boolean debug = false;
	private static final boolean useDepthLimit = true;
	private static final int MonteCarloCount = 4;
	private static final int depthIncrement = 1;
	private static final double explorationConstant = 40;
	private static final int nodesPerMetaGameTurn = 0;
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
		//TODO determine C value/(heuristics?)
		this.timeout = timeout;
		stateMachine = getStateMachine();
		players = stateMachine.getRoles();
		role = getRole();
		playerNumber = findPlayerNum();
		if(playerNumber < 0) System.out.println("We've got a problem: role does not exist.");
		MachineState initialState = stateMachine.getInitialState();
		gameTree = new GameNode(initialState, null, null);
		gameTree.initializeUAMC(players, stateMachine);
		//playGameAgainstSearchlight(40);
	}

	private void playGameAgainstSearchlight(int CValue) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		MachineState initialState = stateMachine.getInitialState();

	}

	private Move determineSearchLightMove(Role role, MachineState state) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException
	{
		List<Move> moves = stateMachine.getLegalMoves(state, getRole());
		Move selection = (moves.get(new Random().nextInt(moves.size())));
		List<Move> movesInRandomOrder = new ArrayList<Move>();
		while(!moves.isEmpty()) {
		    Move aMove = moves.get(r.nextInt(moves.size()));
		    movesInRandomOrder.add(aMove);
		    moves.remove(aMove);
		}
		int maxGoal = 0;
		for(Move moveUnderConsideration : movesInRandomOrder) {
		    MachineState nextState = stateMachine.getNextState(state, stateMachine.getRandomJointMove(state, role, moveUnderConsideration));
		    if(stateMachine.isTerminal(nextState)) {
		        if(stateMachine.getGoal(nextState, getRole()) == 0) {
		            continue;
		        } else if(stateMachine.getGoal(nextState, getRole()) == 100) {
	                selection = moveUnderConsideration;
	                break;
		        } else {
		        	if (stateMachine.getGoal(nextState, getRole()) > maxGoal)
		        	{
		        		selection = moveUnderConsideration;
		        		maxGoal = stateMachine.getGoal(nextState, getRole());
		        	}
		        	continue;
		        }
		    }
		    boolean forcedLoss = false;
		    for(List<Move> jointMove : stateMachine.getLegalJointMoves(nextState)) {
		        MachineState nextNextState = stateMachine.getNextState(nextState, jointMove);
		        if(stateMachine.isTerminal(nextNextState)) {
		            if(stateMachine.getGoal(nextNextState, getRole()) == 0) {
		                forcedLoss = true;
		                break;
		            }
		        }
		    }
		    if(!forcedLoss) {
		        selection = moveUnderConsideration;
		    }
		}
		return selection;
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

	private GameNode select(GameNode node) throws MoveDefinitionException
	{
		while(true)
		{
			if(node.visits == 0 || stateMachine.isTerminal(node.state))
			{
				if(!node.isUAMCInitialized()) node.initializeUAMC(players, stateMachine);
				return node;
			}
			for (GameNode child : node.children)
			{
				if(child.visits == 0)
				{
					if(!child.isUAMCInitialized()) child.initializeUAMC(players, stateMachine);
					return child;
				}
			}

			List<Move> jointMove = new ArrayList<Move>();
			for(int player = 0; player < players.size(); player++)
			{
				double bestScore = -1;
				Move bestMove = null;
				for(Move move : node.utilitiesAndMoveCounts.get(player).navigableKeySet())
				{
					double newscore = selectfn(node, player, move);
					if(newscore > bestScore)
					{
						bestScore = newscore;
						bestMove = move;
					}
				}
				jointMove.add(bestMove);
			}
			for(GameNode child : node.children)
			{
				if(child.jointMove.equals(jointMove))
				{
					if(!child.isUAMCInitialized()) child.initializeUAMC(players, stateMachine);
					return select(child);
				}
			}
		}
	}

	private double selectfn(GameNode node, int playerNumber, Move move) {
		double moveUtility = node.utilitiesAndMoveCounts.get(playerNumber).get(move).left;
		int moveVisits = node.utilitiesAndMoveCounts.get(playerNumber).get(move).right;
		return moveUtility + explorationConstant * Math.sqrt(2 * Math.log(node.visits) / moveVisits);
	}

	private boolean expand(GameNode node) throws MoveDefinitionException, TransitionDefinitionException
	{
		if(stateMachine.isTerminal(node.state)) return false;
		for(List<Move> jointMove: stateMachine.getLegalJointMoves(node.state))
		{
			MachineState newstate = stateMachine.getNextState(node.state, jointMove);
			GameNode newNode = new GameNode(newstate, node, jointMove);
			node.children.add(newNode);
		}
		return true;
	}

	private void backpropagate(GameNode node, List<Integer> scores, List<Move> jointMove) throws MoveDefinitionException
	{
		if(!node.isUAMCInitialized()) node.initializeUAMC(players, stateMachine);
		try
		{
		for(int player = 0; player < players.size(); player++)
		{
			Pair<Double, Integer> utilityAndVisits = node.utilitiesAndMoveCounts.get(player).get(jointMove.get(player));
			int visits = utilityAndVisits.right;
			double utility = utilityAndVisits.left;
			utilityAndVisits.left = (utility * visits + scores.get(player)) / (visits + 1);
			utilityAndVisits.right++;
			node.utilitiesAndMoveCounts.get(player).put(jointMove.get(player), utilityAndVisits);
		}
		node.visits++;
		if(node.parent != null) backpropagate(node.parent, scores, node.jointMove);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

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

	private List<Integer> depthCharge(MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		int depth = 0;
		while (!stateMachine.isTerminal(state) && depth < depthLimit) {
            state = stateMachine.getNextStateDestructively(state, stateMachine.getRandomJointMove(state));
		}
		if(depth == depthLimit) System.out.println("Depth limit reached");
		List<Integer> scores = new ArrayList<Integer>();
		for(int i = 0; i < players.size(); i++)
		{
			try
			{
				scores.add(stateMachine.getGoal(state, players.get(i)));
			}
			catch(GoalDefinitionException e)
			{
				System.out.println("Undefined goal");
				scores.add(50);
			}
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

	public Move getBestMove(Role role, MachineState currentState) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException
	{
		//TODO maintain old game tree
		//TODO Add threading
			Move bestMove = stateMachine.getRandomMove(currentState, role);
			if(stateMachine.getLegalMoves(currentState, role).size() > 1) {
			gameTree = new GameNode(currentState, null, null);
			gameTree.initializeUAMC(players, stateMachine);
			int nodeCount = 0;
			while(true)
			{
				if(nodeCount % 100 == 0)
				{
					System.out.println("Nodecount: " + nodeCount + " Free memory: " + Runtime.getRuntime().freeMemory());
				}
				GameNode node = select(gameTree);
				boolean nonTerminal = expand(node);
				List<Move> jointMove = stateMachine.getRandomJointMove(node.state);
				List<Integer> scores = depthCharge(stateMachine.getNextState(node.state, jointMove));
				backpropagate(node, scores, jointMove);
				nodeCount++;
				if(timedOut()) break;
			}

			double bestScore = -1;

			TreeMap<Move, Pair<Double, Integer> > movesToUtils = gameTree.utilitiesAndMoveCounts.get(playerNumber);
			for(Move move : movesToUtils.navigableKeySet())
			{
				double newScore = movesToUtils.get(move).left;
				if(newScore > bestScore)
				{
					bestScore = newScore;
					bestMove = move;
				}
			}
		}
		return bestMove;
	}

//	private List<Integer> minimaxDepthCharge(GameNode node) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
//		List<Integer> scores = new ArrayList<Integer>();
//		scores.add(0);
//		scores.add(0);
//		int opponent = playerNumber ^ 1;
//		for(int i = 0; i < node.children.size(); i++)
//		{
//			List<Integer> chargeScores = depthCharge(node.children.get(i).state);
//			if(node.isMaxNode)
//			{
//				if(chargeScores.get(playerNumber) > scores.get(playerNumber))
//				{
//					scores = chargeScores;
//				}
//			}
//			else
//			{
//				if(chargeScores.get(opponent) > scores.get(opponent))
//				{
//					scores = chargeScores;
//				}
//			}
//		}
//		return scores;
//	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		MachineState currentState = getCurrentState();
		this.timeout = timeout;
		return getBestMove(role, currentState);
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
