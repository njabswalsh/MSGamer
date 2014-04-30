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
	private int depthLimit = 1;
	private long timeout = 0;
	private List<Role> players;
	private int playerNumber;
	private Role role;
	private Random r = new Random();
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

	private int monteCarlo(Role role, MachineState state, int count) throws GoalDefinitionException, MoveDefinitionException
	{
		int total = 0;
		for(int i = 0; i < count; i++)
		{
			total = total + depthCharge(role, state);
		}
		return total / count;
	}

	private int depthCharge(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException {
		if (stateMachine.isTerminal(state)) {
			return stateMachine.getGoal(state, role);
		}
		List<Move> moves = new ArrayList<Move>();
		for(int i = 0; i < players.size(); i++ )
		{
			List<Move> actions = stateMachine.getLegalMoves(state, players.get(i));
			moves.add(actions.get(r.nextInt(actions.size())));
		}
		MachineState next = null;
		try
		{
			next = stateMachine.getNextState(state, moves);
		} catch (TransitionDefinitionException e) {
			e.printStackTrace();
		}
		return depthCharge(role, next);
	}

	@SuppressWarnings("unused")
	private int goalProximityEval(Role role, MachineState state) throws GoalDefinitionException
	{
		return stateMachine.getGoal(state, role);
	}

	private int evalFunction(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException
	{
		return monteCarlo(role, state, MonteCarloCount);
		//return focusEval(players.get(playerNumber ^ 1), state);
		//return goalProximityEval(role, state);
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

				int result = maxscore(role, newstate, alpha, beta, level+1);
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
		List<Move> legalMoves = stateMachine.getLegalMoves(currentState, role);
		Move bestMove = legalMoves.get(0);
		Move bestMoveFromPastLevel = bestMove;
		int score = 0;
		if(legalMoves.size() > 1)
		{
			while (true)
			{
				for (int i = 0; i < legalMoves.size(); i++)
			    {
					if(timedOut())
					{
						return bestMoveFromPastLevel;
					}
					int result = minscore(role, legalMoves.get(i), currentState, 0, 100, 0);
					if (result == 100)
					{
						//System.out.println("Found winning move: " + legalMoves.get(i));
						return legalMoves.get(i);
					}
					if (result > score)
					{
						score = result;
						bestMove = legalMoves.get(i);
						//System.out.println("Best score: " + score + " Best move: " + bestMove);
					}
				}
				bestMoveFromPastLevel = bestMove;
				depthLimit++;
			}
		}
		return bestMoveFromPastLevel;
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
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {

	}

	@Override
	public String getName() {
		return "MSGamer";
	}

}
