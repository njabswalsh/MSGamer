package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

public class GameNode {
	GameNode parent;
	MachineState state;
	int visits;
	int utility;
	List<GameNode> children;
	Move move;
	public GameNode(MachineState state, GameNode parent, Move move)
	{
		this.parent = parent;
		this.state = state;
		this.move = move;
		this.visits = 0;
		this.utility = 0;
		this.children = new ArrayList<GameNode>();
	}
}
