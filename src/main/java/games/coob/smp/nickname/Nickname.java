package games.coob.smp.nickname;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's nickname: the text plus how it is coloured (one colour, a
 * gradient, or rainbow) and styled. The text only ever contains letters,
 * numbers, underscores and spaces, so it can't inject formatting.
 */
@Getter
@Setter
public final class Nickname {

	/** Null means "use the account name" (only the style is changed). */
	private String text;
	/** One colour, or two or more for a gradient. Empty means white. */
	private List<TextColor> colors = new ArrayList<>();
	private boolean rainbow;
	private boolean bold;
	private boolean italic;
	private boolean underlined;

	public boolean isEmpty() {
		return text == null && colors.isEmpty() && !rainbow && !bold && !italic && !underlined;
	}

	/** The coloured name, using the account name when no custom text is set. */
	public Component render(String accountName) {
		String name = text != null ? text : accountName;
		StringBuilder open = new StringBuilder();
		StringBuilder close = new StringBuilder();

		if (rainbow) {
			wrap(open, close, "rainbow");
		} else if (colors.size() >= 2) {
			StringBuilder gradient = new StringBuilder("gradient");
			for (TextColor color : colors)
				gradient.append(':').append(color.asHexString());
			wrap(open, close, gradient.toString());
		} else if (colors.size() == 1) {
			wrap(open, close, "color:" + colors.getFirst().asHexString());
		}
		if (bold)
			wrap(open, close, "bold");
		if (italic)
			wrap(open, close, "italic");
		if (underlined)
			wrap(open, close, "underlined");

		// Escaped as well, so a name can never be read as formatting
		return MiniMessage.miniMessage().deserialize(open + MiniMessage.miniMessage().escapeTags(name)
				+ close.reverse().toString());
	}

	private static void wrap(StringBuilder open, StringBuilder close, String tag) {
		String name = tag.contains(":") ? tag.substring(0, tag.indexOf(':')) : tag;
		open.append('<').append(tag).append('>');
		// Built reversed so closing tags come out in the right order
		close.append(new StringBuilder("</" + name + ">").reverse());
	}

	/** Just the text, for comparing names. */
	public String plain(String accountName) {
		return text != null ? text : accountName;
	}
}
