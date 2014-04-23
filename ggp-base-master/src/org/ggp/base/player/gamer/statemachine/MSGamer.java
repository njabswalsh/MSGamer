package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.List;

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

	@Override
	public StateMachine getInitialStateMachine() {
		return new ProverStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		// TODO Auto-generated method stub

	}


	private int minscore(Role role, Move action, MachineState state, int alpha, int beta) throws MoveDefinitionException {
		StateMachine stateMachine = getStateMachine();
		List<Role> players = stateMachine.getRoles();
		Role opponent = players.get(0);
		if (players.get(0) == role) {
			opponent = players.get(1);
		}
		List<Move> actions = stateMachine.getLegalMoves(state, opponent);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> testMove = new ArrayList<Move>();
			if (role ==players.get(0)) {
				testMove.add(action);
				testMove.add(actions.get(i));
			} else {
				testMove.add(actions.get(i));
				testMove.add(action);
			}
			MachineState newstate = stateMachine.getNextState(state, testMove);
			int result = maxscore(role, newstate, alpha, beta);
			beta = Math.min(beta, result);
			if (beta <= alpha) return alpha;
		}
		return beta;
	}


	private int maxscore(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine stateMachine = getStateMachine();
		if (stateMachine.isTerminal(state)) {
			List<Integer> goals = stateMachine.getGoals(state);
			return goals.get(0);
		}
		List<Move> legalMoves = stateMachine.getLegalMoves(state, role);
		int score = 0;
		for (int i = 0; i < legalMoves.size(); i++) {
			List<Move> testMove = new ArrayList<Move>();
			testMove.add(legalMoves.get(i));
			int result = maxscore(role, stateMachine.getNextState(state, testMove));
			if (result>score) score = result;
		}
		return score;
	}


	public Move getBestMoveFromCurrentState(Role role, MachineState currentState, StateMachine stateMachine) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException
	{
		List<Move> legalMoves = stateMachine.getLegalMoves(currentState, role);
		Move bestMove = legalMoves.get(0);
		int score = 0;
		for (int i = 0; i < legalMoves.size(); i++)
	    {
			List<Move> nextMove = new ArrayList<Move>();
			nextMove.add(legalMoves.get(i));
			int result = maxscore(role, stateMachine.getNextState(currentState, nextMove));
			if (result == 100)
			{
				return legalMoves.get(i);
			}
			if (result > score)
			{
				score = result;
				bestMove = legalMoves.get(i);
			}
		}
		return bestMove;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		StateMachine stateMachine = getStateMachine();
		MachineState currentState = getCurrentState();
		Role role = getRole();

		return getBestMoveFromCurrentState(role, currentState, stateMachine);
	}

	@Override
	public void stateMachineStop() {

	}

	@Override
	public void stateMachineAbort() {

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {

	}

	@Override
	public String getName() {
		return "MSGamer";
	}

}
