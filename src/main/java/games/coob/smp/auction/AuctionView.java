package games.coob.smp.auction;

/**
 * An open auction menu, redrawn whenever the auction house changes, so
 * nobody acts on an offer or listing that is already gone.
 */
public interface AuctionView {

	void refresh();
}
