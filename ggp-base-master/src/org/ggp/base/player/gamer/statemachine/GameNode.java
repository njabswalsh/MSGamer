package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

public class GameNode {
	GameNode parent;
	boolean isMaxNode;
	MachineState state;
	int visits;
	List<Double> utilities;
	List<GameNode> children;
	Move move;
	public GameNode(MachineState state, GameNode parent, Move move, boolean isMaxNode)
	{
		this.isMaxNode = isMaxNode;
		this.parent = parent;
		this.state = state;
		this.move = move;
		this.visits = 0;
		this.utilities = new ArrayList<Double>();
		this.utilities.add(0.0);
		this.utilities.add(0.0);
		this.children = new ArrayList<GameNode>();
	}
}
