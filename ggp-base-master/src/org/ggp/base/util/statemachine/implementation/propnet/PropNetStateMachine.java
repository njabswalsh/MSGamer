package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.propnet.factory.PropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;


@SuppressWarnings("unused")
public class PropNetStateMachine extends StateMachine {
    private static final int MINIMUM_TRIMMABLE_PERCENTAGE = 15;
	/** The underlying proposition network  */
    private PropNet propNet;

    /** The topological ordering of the propositions */
    private List<Proposition> ordering;

    /** The player roles */
    private List<Role> roles;

    private MachineState initialState;

    private Collection<Proposition> baseProps;

    private MachineState currentState;
	private Map<Role, Move> noopMap;

	private List<Gdl> gameDescription;
	private ProverStateMachine prover = null;



//    public PropNetStateMachine(StateMachine stateMachine) {
//		// TODO Auto-generated constructor stub
//	}

	/**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
        	gameDescription = description;
			propNet = OptimizingPropNetFactory.create(description);
			//propNet.renderToFile("PropNetRender.dot");
		} catch (InterruptedException e) {
			System.out.println("Interupted: Using non-optimized prop net");
			propNet = PropNetFactory.create(description);
		}
        roles = propNet.getRoles();

        computeInitialState();
        noopMap = createNoopMap();
        trimPropNet();

        baseProps = propNet.getBasePropositions().values();
        ordering = getOrdering(propNet);

    }

	private Map<Role, Move> createNoopMap() {
		System.out.println("Searching for noops...");
		Map<Role, Move> map = new HashMap<Role, Move>();
		for(Role r : roles)
		{
			for(Proposition p : propNet.getLegalPropositions().get(r))
			{
				if(p.getName().toString().contains("noop"))
				{
					System.out.println("Found noop for " + r + ": " + p.getName().toString());
					map.put(r, getMoveFromProposition(p));
					break;
				}
			}
		}
		return map;
	}

	private void computeInitialState() {
		propNet.getInitProposition().setValue(true);
		initialState = getStateFromBase();
		propNet.getInitProposition().setValue(false);
	}


	private void trimPropNet()
	{
		System.out.println("Attempting to trim prop net...");
		Set<Component> usefulComps = new HashSet<Component>();
		Set<Component> markedComps = new HashSet<Component>();
		for(Set<Proposition> setP: propNet.getGoalPropositions().values())
		{
			for(Proposition p : setP)
			{
				backwardVisit(p, usefulComps, markedComps);
			}
		}
		backwardVisit(propNet.getTerminalProposition(), usefulComps, markedComps);
		Set<Component> usefulLegals = new HashSet<Component>();
		for(Component c : usefulComps)
		{
			if(propNet.getInputPropositions().values().contains(c))
			{
				usefulLegals.add(propNet.getLegalInputMap().get(c));
			}
		}
		for(Component c : usefulLegals)
		{
			backwardVisit(c, usefulComps, markedComps);
		}
		Set<Component> removeThese = new HashSet<Component>();
		for(Component c: propNet.getComponents())
		{
			if(!usefulComps.contains(c)) removeThese.add(c);
		}
		int trimmable = removeThese.size();
		int total = propNet.getComponents().size();
		if( trimmable > (total * MINIMUM_TRIMMABLE_PERCENTAGE)/ 100)
		{
			for(Component c: removeThese)
			{
				propNet.removeComponent(c);
			}
			System.out.println("Trimmed " + trimmable + " out of " + total + " components from the prop net.");
		}
		else
		{
			System.out.println("Only " + trimmable + " removable components out of " + total + ". Using full prop net.");
		}
	}

	private void backwardVisit(Component comp, Set<Component> usefulComps, Set<Component>markedComps)
	{
		if(!markedComps.contains(comp))
		{
			usefulComps.add(comp);
			markedComps.add(comp);
			for(Component c: comp.getInputs())
			{
				backwardVisit(c, usefulComps, markedComps);
			}
		}
	}
	/**
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state.
	 */
	@Override
	public boolean isTerminal(MachineState state) {
		setStateIfNecessary(state);
		Proposition term = propNet.getTerminalProposition();
		return term.getValue();
	}

	private void setStateIfNecessary(MachineState state) {
		if(currentState != state)
		{
			setBasesFromState(state);
			propagateBaseValues();
		}
	}

	/**
	 * Computes the goal for a role in the current state.
	 * Should return the value of the goal proposition that
	 * is true for that role. If there is not exactly one goal
	 * proposition true for that role, then you should throw a
	 * GoalDefinitionException because the goal is ill-defined.
	 */
	@Override
	public int getGoal(MachineState state, Role role)
	throws GoalDefinitionException {
		//TODO assume that isTerminal is called with the same state if applicable
		setStateIfNecessary(state);
		Set<Proposition> goals = propNet.getGoalPropositions().get(role);
		for(Proposition p : goals)
		{
			if(p.getValue()) return getGoalValue(p);
		}
		return 0;
	}

	/**
	 * Returns the initial state. The initial state can be computed
	 * by only setting the truth value of the INIT proposition to true,
	 * and then computing the resulting state.
	 */
	@Override
	public MachineState getInitialState() {
		return initialState;
	}

	/**
	 * Computes the legal moves for role in state.
	 */
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role)
	{
		//System.out.println("getLegalMoves(): " + role);
		setStateIfNecessary(state);
		Set<Proposition> legalProps = propNet.getLegalPropositions().get(role);

		List<Move> legalMoves = new ArrayList<Move>();

		for(Proposition p : legalProps)
		{
			if(p.getValue())
			{
				legalMoves.add(getMoveFromProposition(p));
			}
		}
		if(legalMoves.size() == 0)
		{
			Move m = noopMap.get(role);
			if(m == null)
			{
				if(prover == null)
				{
					System.out.println("Using the prover to find noop...");
					prover = new ProverStateMachine();
					prover.initialize(gameDescription);
				}
				try {
					return prover.getLegalMoves(state, role);
				} catch (MoveDefinitionException e) {
					e.printStackTrace();
				}
			}
			legalMoves.add(m);
			return legalMoves;
		}
		return legalMoves;
	}

	/**
	 * Computes the next state given state and the list of moves.
	 */
	@Override
	public MachineState getNextState(MachineState state, List<Move> moves)
	throws TransitionDefinitionException {
		//Set to next state
		if(currentState != state) setBasesFromState(state);
		setInputFromMoves(moves);
		propagateBaseValues();
		Set<GdlSentence> contents = new HashSet<GdlSentence>();
		for(Proposition p: propNet.getBasePropositions().values())
		{
			if(p.getSingleInput().getSingleInput().getValue())
			{
				contents.add(p.getName());
				p.setValue(true);
			}
			else
			{
				p.setValue(false);
			}
		}
		currentState = new MachineState(contents);
		return currentState;
	}

	/**
	 * This should compute the topological ordering of propositions.
	 * Each component is either a proposition, logical gate, or transition.
	 * Logical gates and transitions only have propositions as inputs.
	 *
	 * The base propositions and input propositions should always be exempt
	 * from this ordering.
	 *
	 * The base propositions values are set from the MachineState that
	 * operations are performed on and the input propositions are set from
	 * the Moves that operations are performed on as well (if any).
	 *
	 * @return The order in which the truth values of propositions need to be set.
	 */
	public List<Proposition> getOrdering(PropNet pnet)
	{
	    // List to contain the topological ordering.
	    List<Proposition> order = new LinkedList<Proposition>();

		// All of the propositions in the PropNet.
		List<Proposition> propositions = new ArrayList<Proposition>(pnet.getPropositions());

		Collection<Proposition> baseProps = pnet.getBasePropositions().values();
		Collection<Proposition> inputProps = pnet.getInputPropositions().values();

		Set<Component> markedProps = new HashSet<Component>();
		for(Proposition p: propositions)
		{
			if(!markedProps.contains(p) && !baseProps.contains(p) && !inputProps.contains(p))
			{
				visit(p, order, markedProps);
			}
		}
		System.out.println("Got ordering");
		return order;
	}

	private void visit(Component c, List<Proposition> order, Set<Component> markedProps) {
		if(markedProps.contains(c)) return;
		if(!(c instanceof Transition))
		{
			for(Component comp : c.getOutputs())
			{
				visit(comp, order, markedProps);
			}
		}
		markedProps.add(c);
		if(c instanceof Proposition)
		{
			order.add(0, (Proposition)c);
		}
	}

	/* Already implemented for you */
	@Override
	public List<Role> getRoles() {
		return roles;
	}

	/* Helper methods */

	/**
	 * The Input propositions are indexed by (does ?player ?action).
	 *
	 * This translates a list of Moves (backed by a sentence that is simply ?action)
	 * into GdlSentences that can be used to get Propositions from inputPropositions.
	 * and accordingly set their values etc.  This is a naive implementation when coupled with
	 * setting input values, feel free to change this for a more efficient implementation.
	 *
	 * @param moves
	 * @return
	 */
	private Set<GdlSentence> toDoes(List<Move> moves)
	{
		Set<GdlSentence> doeses = new HashSet<GdlSentence>(moves.size());
		Map<Role, Integer> roleIndices = getRoleIndices();

		for (int i = 0; i < roles.size(); i++)
		{
			int index = roleIndices.get(roles.get(i));
			doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
		}
		return doeses;
	}

	/**
	 * Takes in a Legal Proposition and returns the appropriate corresponding Move
	 * @param p
	 * @return a PropNetMove
	 */
	public static Move getMoveFromProposition(Proposition p)
	{
		return new Move(p.getName().get(1));
	}

	/**
	 * Helper method for parsing the value of a goal proposition
	 * @param goalProposition
	 * @return the integer value of the goal proposition
	 */
    private int getGoalValue(Proposition goalProposition)
	{
		GdlRelation relation = (GdlRelation) goalProposition.getName();
		GdlConstant constant = (GdlConstant) relation.get(1);
		return Integer.parseInt(constant.toString());
	}

    //Not the most efficient implementation
    private void setBasesFromState(MachineState state)
    {
    	//System.out.println("setBasesFromState()");
    	Set<GdlSentence> stateContent = state.getContents();
    	for(Proposition p : baseProps)
    	{
    		boolean b = stateContent.contains(p.getName());
    		p.setValue(b);
    	}
    	currentState = state;
    }

	private void setInputFromMoves(List<Move> moves) {
		//System.out.println("setInputFromMoves()");
		Set<GdlSentence> sentences = toDoes(moves);
		Map<GdlSentence, Proposition> inputMap = propNet.getInputPropositions();

		for(Proposition p: propNet.getInputPropositions().values())
		{
			p.setValue(sentences.contains(p.getName()));
		}
	}

    private void propagateBaseValues()
    {
    	for(Proposition p : ordering)
    	{
    		if(p.getInputs().size() > 0) p.setValue(p.getSingleInput().getValue());
    	}
    }

	/**
	 * A Naive implementation that computes a PropNetMachineState
	 * from the true BasePropositions.  This is correct but slower than more advanced implementations
	 * You need not use this method!
	 * @return PropNetMachineState
	 */
	public MachineState getStateFromBase()
	{
		Set<GdlSentence> contents = new HashSet<GdlSentence>();
		for (Proposition p : propNet.getBasePropositions().values())
		{
			p.setValue(p.getSingleInput().getValue());
			if (p.getValue())
			{
				contents.add(p.getName());
			}

		}
		return new MachineState(contents);
	}


	/*
	 * Below this point is an implementation for backwards reasoning
	 *
	 */
	//	private boolean getPropValue(Component c)
	//	{
	//		if(c instanceof Constant) return c.getValue();
	//		if(c instanceof And) return getAndPropValue(c);
	//		if(c instanceof Or) return getOrPropValue(c);
	//		if(c instanceof Not) return !getPropValue(c.getSingleInput());
	//
	//		Collection<Proposition> baseProps = propNet.getBasePropositions().values();
	//		if(baseProps.contains(c))
	//		{
	//			//System.out.println("It worked!!!");
	//			return c.getValue();
	//		}
	//		Collection<Proposition> inputProps = propNet.getInputPropositions().values();
	//		if(baseProps.contains(c))
	//		{
	//			//System.out.println("It worked!!!");
	//			return c.getValue();
	//		}
	//
	//		if(c.getInputs().size() == 0)
	//		{
	//			//System.out.println("It worked...");
	//			return c.getValue();
	//		}
	//		return getPropValue(c.getSingleInput());
	//	}
	//
	//	private boolean getAndPropValue(Component c)
	//	{
	//		Set<Component> inputs = c.getInputs();
	//		for(Component in : inputs)
	//		{
	//			if(!getPropValue(in)) return false;
	//		}
	//		return true;
	//	}
	//	private boolean getOrPropValue(Component c)
	//	{
	//		Set<Component> inputs = c.getInputs();
	//		for(Component in : inputs)
	//		{
	//			if(getPropValue(in)) return true;
	//		}
	//		return false;
	//	}
}