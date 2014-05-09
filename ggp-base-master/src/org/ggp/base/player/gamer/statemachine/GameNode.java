package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.ggp.base.util.Pair;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

public class GameNode {
	GameNode parent;
	MachineState state;
	int visits;
	List<GameNode> children;
	List<Move> jointMove;
	List< TreeMap<Move, Pair<Long, Integer> > > utilitiesAndMoveCounts;
	boolean UAMCInitialized = false;
	public GameNode(MachineState state, GameNode parent, List<Move> jointMove)
	{
		this.parent = parent;
		this.state = state;
		this.jointMove = jointMove;
		this.visits = 0;
		this.utilitiesAndMoveCounts = new ArrayList< TreeMap<Move, Pair<Long, Integer> > >();
		this.children = new ArrayList<GameNode>();
	}

	public void initializeUAMC(List<Role> players, StateMachine stateMachine) throws MoveDefinitionException
	{
		for(int i = 0; i < players.size(); i++)
		{
			List<Move> legals = stateMachine.getLegalMoves(state, players.get(i));
			TreeMap<Move, Pair<Long, Integer> > map = new TreeMap<Move, Pair<Long, Integer> >();
			for(Move move : legals)
			{
				Pair<Long, Integer> pair = Pair.of((long)0,0);
				map.put(move, pair);
			}
			utilitiesAndMoveCounts.add(map);
		}
		UAMCInitialized = true;
	}

	public boolean isUAMCInitialized()
	{
		return UAMCInitialized;
	}
}
