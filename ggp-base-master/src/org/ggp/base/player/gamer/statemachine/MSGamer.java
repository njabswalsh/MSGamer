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
import org.ggp.base.util.statemachine.implementation.propnet.PropNetStateMachine;

public class MSGamer extends StateMachineGamer {
	private static final long serverTimeBuffer = 2500;
	private static final boolean debug = false;
	private static final boolean useDepthLimit = true;
	private static final int MonteCarloCount = 4;
	private static final int depthIncrement = 1;
	private static final double explorationConstant = 40;
	private static final int nodesPerMetaGameTurn = 0;
	private static final int numberOfDepthTests = 4;
	private int depthLimit = 200;
	private long timeout = 0;
	private List<Role> players;
	private int playerNumber;
	private Role role;
	private Random r = new Random();
	private GameNode gameTree;
	StateMachine stateMachine;
	StateMachine testStateMachine;
	@Override
	public StateMachine getInitialStateMachine()
	{
		System.out.println("Initialized");
		return new PropNetStateMachine();
		//return new ProverStateMachine();
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
		int nodeCount = 0;
		while(!timedOut())
		{
			iterateMCTS();
			nodeCount++;
		}
		System.out.println("Nodecount: " + nodeCount + " Free memory: " + Runtime.getRuntime().freeMemory());
		//long averageDepthChargeTime = getAverageDepthChargeTime(numberOfDepthTests, initialState);
		//long averageMCTSIteration = getAverageMCTSIteration(numberOfDepthTests);
		//System.out.println("Average Depth Charge: " + averageDepthChargeTime + " Average MCTSIteration: " + averageMCTSIteration);
		//playGameAgainstSearchlight(40);
	}

	private long getAverageMCTSIteration(int n) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		long sum = 0;
		for(int i = 0; i < n; i++)
		{
			long startTime = System.currentTimeMillis();
			iterateMCTS();
			long endTime = System.currentTimeMillis();
			sum += endTime - startTime;
		}
		return sum / n;
	}

	private long getAverageDepthChargeTime(int n, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
	{
		long sum = 0;
		for(int i = 0; i < n; i++)
		{
			long startTime = System.currentTimeMillis();
			depthCharge(state);
			long endTime = System.currentTimeMillis();
			sum += endTime - startTime;
		}
		return sum / n;
	}

	private void playGameAgainstSearchlight(int CValue, MachineState initialState) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException
	{


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
			boolean nodeIsTerminal = stateMachine.isTerminal(node.state);
			if(node.visits == 0 || nodeIsTerminal)
			{
				if(!node.isUAMCInitialized() && !nodeIsTerminal) node.initializeUAMC(players, stateMachine);
				return node;
			}
			for (GameNode child : node.children)
			{
				if(child.visits == 0)
				{
					if(!child.isUAMCInitialized() && !stateMachine.isTerminal(child.state)) child.initializeUAMC(players, stateMachine);
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
		int moveVisits = node.utilitiesAndMoveCounts.get(playerNumber).get(move).right;
		double moveUtility = (double)node.utilitiesAndMoveCounts.get(playerNumber).get(move).left / moveVisits;
		return moveUtility + explorationConstant * Math.sqrt(2 * Math.log(node.visits) / moveVisits);
	}

	private boolean expand(GameNode node) throws MoveDefinitionException, TransitionDefinitionException
	{
		if(stateMachine.isTerminal(node.state)) return true;
		for(List<Move> jointMove: stateMachine.getLegalJointMoves(node.state))
		{
			MachineState newstate = stateMachine.getNextState(node.state, jointMove);
			GameNode newNode = new GameNode(newstate, node, jointMove);
			node.children.add(newNode);
		}
		return false;
	}

	private void backpropagate(GameNode node, List<Integer> scores, List<Move> jointMove) throws MoveDefinitionException
	{
		if(!node.isUAMCInitialized()) node.initializeUAMC(players, stateMachine);
		try
		{
		for(int player = 0; player < players.size(); player++)
		{
			Pair<Long, Integer> utilityAndVisits = node.utilitiesAndMoveCounts.get(player).get(jointMove.get(player));
			long utility = utilityAndVisits.left;
			utilityAndVisits.left = utility + scores.get(player);
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

	private List<Integer> depthCharge(MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		while (!stateMachine.isTerminal(state)) {
            state = stateMachine.getNextState(state, stateMachine.getRandomJointMove(state));
		}
		List<Integer> scores = getScoresForState(state);
		return scores;
	}

	private List<Integer> getScoresForState(MachineState state) {
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
			e.printStackTrace();
			scores.add(50);
		}
	}
	return scores;
}

	public Move getBestMove(Role role, MachineState currentState) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException
	{
		boolean isChildTree = false;
		if(!gameTree.state.equals(currentState))
		{
			for(GameNode child : gameTree.children)
			{
				if(child.state.equals(currentState))
				{
					gameTree = child;
					gameTree.parent = null;
					isChildTree = true;
					break;
				}
			}
		}
		else
		{
			isChildTree = true;
			System.out.println("First turn");
		}
		if(!isChildTree)
		{
			System.out.println("This shouldn't happen too often");
			gameTree = new GameNode(currentState, null, null);
			gameTree.initializeUAMC(players, stateMachine);
		}
		Move bestMove = stateMachine.getRandomMove(currentState, role);
		int nodeCount = 0;
		while(!timedOut())
		{
			iterateMCTS();
			nodeCount++;
		}
		System.out.println("Nodecount: " + nodeCount + " Free memory: " + Runtime.getRuntime().freeMemory());
		double bestScore = -1;
		int bestMoveVisits = 0;
		TreeMap<Move, Pair<Long, Integer> > movesToUtils = gameTree.utilitiesAndMoveCounts.get(playerNumber);
		for(Move move : movesToUtils.navigableKeySet())
		{
			long util = movesToUtils.get(move).left;
			int visits = movesToUtils.get(move).right;
			double newScore = (double)util / visits;
			System.out.println("Move: " + move + " score: " + newScore + " Visits: " + visits + " Util: " + util);
			if(newScore > bestScore || (newScore == bestScore && visits > bestMoveVisits))
			{
				bestScore = newScore;
				bestMove = move;
				bestMoveVisits = visits;
			}
		}
		//TODO change gameTree here
		return bestMove;
	}

	private void iterateMCTS() throws MoveDefinitionException,
			TransitionDefinitionException, GoalDefinitionException {
		GameNode node = select(gameTree);
		boolean terminal = expand(node);
		if(!terminal) // Fails if first state is terminal (shouldn't be a problem)
		{
			List<Move> jointMove = stateMachine.getRandomJointMove(node.state);
			//System.out.println("Got Joint move. ");
			List<Integer> scores = depthCharge(stateMachine.getNextState(node.state, jointMove));
			backpropagate(node, scores, jointMove);
		}
		else
		{
			List<Integer> scores = getScoresForState(node.state);
			if(node.parent == null)
			{
				System.out.println("issues");
			}
			backpropagate(node.parent, scores, node.jointMove);
		}
	}

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
