package br.com.gamemods.minecity.api.command;

import br.com.gamemods.minecity.api.StringUtil;
import br.com.gamemods.minecity.api.permission.ExceptFlagHolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.Format;
import java.text.NumberFormat;
import java.util.*;

import static br.com.gamemods.minecity.api.StringUtil.identity;
import static br.com.gamemods.minecity.api.StringUtil.replaceTokens;
import static br.com.gamemods.minecity.api.command.LegacyFormat.*;

public class MessageTransformer
{
    private Map<String, Component> messages = new HashMap<>();
    private Document doc;
    private DocumentBuilder documentBuilder;
    {
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            documentBuilder = factory.newDocumentBuilder();
        }
        catch(ParserConfigurationException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void parseXML(InputStream in) throws IOException, SAXException
    {
        Element root;
        doc = documentBuilder.parse(in);
        root = doc.getDocumentElement();
        if(!"minecity-messages".equals(root.getTagName()))
            throw new SAXException("This is not a minecity-messages XML! Root:"+root.getTagName());

        NodeList nodes = root.getElementsByTagName("msg");
        int len = nodes.getLength();
        for(int i = 0; i < len; i++)
        {
            Element msgTag = (Element) nodes.item(i);
            Element parent = msgTag;
            Stack<String> stack = new Stack<>();
            while(!root.equals(parent = (Element) parent.getParentNode()))
                stack.push(identity(parent.getTagName()));

            StringBuilder sb = new StringBuilder();
            String id;
            while(!stack.isEmpty())
                sb.append(stack.pop()).append(".");
            sb.append(msgTag.getAttribute("id"));
            id = sb.toString();

            Component component = parse(msgTag);
            messages.put(id, component);
        }
    }

    public String toLegacy(Message message)
    {
        Component component = messages.get(message.getId());
        try
        {
            if(component != null)
                component = component.clone();
            else
                component = parse(message.getFallback());
        }
        catch(SAXException | CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }

        Object[][] args = message.getArgs();
        if(args != null)
            component.apply(Locale.getDefault(), args);

        return component.toString();
    }

    public String toSimpleText(Message message)
    {
        return LegacyFormat.clear(toLegacy(message));
    }

    protected Component parse(String message) throws SAXException
    {
        if(message.startsWith("<msg>"))
            try
            {
                Document doc = documentBuilder.parse(new ByteArrayInputStream(message.getBytes()));
                return parse(doc.getDocumentElement());
            }
            catch(IOException e)
            {
                throw new RuntimeException(e);
            }
        else
        {
            TextComponent textComponent = parseText(message);
            if(textComponent.color == null)
                textComponent.color = RESET;
            return textComponent;
        }
    }

    protected TextComponent parseText(String text)
    {
        TextComponent subStructure = new TextComponent("");
        TextComponent last = subStructure;
        StringBuilder sb = new StringBuilder(), token = new StringBuilder();
        boolean buildingToken = false;
        char[] chars = text.toCharArray();
        for(int i = 0; i < chars.length; i++)
        {
            char c = chars[i];
            if(buildingToken)
            {
                if(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '_' || c == '-')
                {
                    token.append(c);
                    continue;
                }

                buildingToken = false;
                if(c == '}')
                {
                    String key = token.toString();
                    last.tokens.put(sb.length(), key);
                    continue;
                }
                else
                    sb.append("${").append(token);

                token.setLength(0);
            }

            LegacyFormat format;
            if(c == MARK && i+1 < chars.length && (format = forCode(chars[i+1])) != null)
            {
                i++;
                if(sb.length() > 0)
                {
                    last.text = sb.toString();
                    sb.setLength(0);

                    TextComponent next = new TextComponent("");
                    if(format.format)
                    {
                        next.color = last.color;
                        next.style.add(format);
                        next.style.addAll(last.style);
                    }
                    else if(format != RESET)
                    {
                        next.color = format;
                        next.style.addAll(last.style);
                    }

                    subStructure.extra.add(next);
                    next.parent = subStructure;
                    last = next;
                }
                else
                {
                    if(format.format)
                        last.style.add(format);
                    else if(format == RESET)
                        last.color = null;
                    else
                        last.color = format;
                }
            }
            else if(c == '$' && i+3 < chars.length && chars[i+1] == '{' && chars[i+2] != '}')
            {
                i++;
                buildingToken = true;
            }
            else
                sb.append(c);
        }

        last.text = sb.toString();

        return subStructure;
    }

    protected Component parse(Element root)
    {
        Deque<Struct> queue = new ArrayDeque<>();
        TextComponent rootComponent = new TextComponent("");
        rootComponent.color = RESET;
        NodeList rootNodes = root.getChildNodes();
        int l = rootNodes.getLength();
        for(int n = 0; n < l; n++)
            queue.add(new Struct(rootNodes.item(n), rootComponent));

        boolean firstText = true;
        Struct item;
        queue:
        while((item = queue.poll()) != null)
        {
            Node node = item.element;
            short nodeType = node.getNodeType();
            Component component = item.component;
            if(nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE)
            {
                String text = node.getTextContent();
                if(nodeType == Node.TEXT_NODE)
                {
                    text = text.replaceAll("\\s+", " ");

                    if(text.length() > 2)
                    {
                        char lc = text.charAt(0), rc = text.charAt(text.length()-1);
                        text = text.trim();

                        if(!firstText && (text.length() == 0 || text.charAt(0) != lc))
                            text = " "+text;

                        if(!queue.isEmpty() && (text.length() <= 1 || text.charAt(text.length()-1) != rc))
                            text += " ";
                    }

                    firstText = false;
                }

                TextComponent subStructure = parseText(text);
                if(component instanceof TextComponent)
                {
                    TextComponent current = (TextComponent)component;
                    if(current.color == null && current.text.isEmpty() && current.style.isEmpty() && current.extra.isEmpty())
                    {
                        current.text = subStructure.text;
                        current.tokens = subStructure.tokens;
                        current.color = subStructure.color;
                        current.style = subStructure.style;
                        current.extra = subStructure.extra;
                        current.extra.forEach(c-> c.parent=current);
                    }
                    else if(current.text.isEmpty() && current.extra.isEmpty())
                    {
                        current.text = subStructure.text;
                        current.tokens = subStructure.tokens;
                        if(subStructure.color != null)
                            current.color = subStructure.color;
                        current.style.addAll(subStructure.style);
                        current.extra = subStructure.extra;
                        current.extra.forEach(c-> c.parent=current);
                    }
                    else
                    {
                        component.extra.add(subStructure);
                        subStructure.parent = component;
                    }
                }
            }
            else if(nodeType == Node.ELEMENT_NODE)
            {
                Element element = (Element) node;
                LegacyFormat format;
                switch(element.getTagName())
                {
                    case "black": format = BLACK; break;
                    case "darkblue": format = DARK_BLUE; break;
                    case "darkgreen": format = DARK_GREEN; break;
                    case "darkaqua": format = DARK_AQUA; break;
                    case "darkred": format = DARK_RED; break;
                    case "darkpurple": format = DARK_PURPLE; break;
                    case "gold": format = GOLD; break;
                    case "gray": format = GRAY; break;
                    case "darkgray": format = DARK_GRAY; break;
                    case "blue": format = BLUE; break;
                    case "green": format = GREEN; break;
                    case "aqua": format = AQUA; break;
                    case "red": format = RED; break;
                    case "lightpurple": format = LIGHT_PURPLE; break;
                    case "yellow": format = YELLOW; break;
                    case "white": format = WHITE; break;
                    case "reset": format = RESET; break;
                    case "o": format = MAGIC; break;
                    case "b": format = BOLD; break;
                    case "s": format = STRIKE; break;
                    case "u": format = UNDERLINE; break;
                    case "i": format = ITALIC; break;
                    default: continue queue;
                }

                Component extra = new TextComponent("");
                extra.parent = component;
                component.extra.add(extra);
                extra.style.addAll(component.style);
                if(format.format)
                    extra.style.add(format);
                else
                {
                    extra.color = format;
                    if(format == RESET)
                        extra.color = null;
                }
                component = extra;
            }

            NodeList childNodes = node.getChildNodes();
            int len = childNodes.getLength();
            for(int i = len-1; i >= 0; i--)
                queue.push(new Struct(childNodes.item(i), component));
        }

        return rootComponent;
    }

    private class Struct
    {
        private Node element;
        private Component component;

        private Struct(Node element, Component component)
        {
            this.element = element;
            this.component = component;
        }

        @Override
        public String toString()
        {
            return element.toString();
        }
    }

    protected abstract class Component implements Cloneable
    {
        public LegacyFormat color = null;
        public EnumSet<LegacyFormat> style = EnumSet.noneOf(LegacyFormat.class);
        public Click click;
        public Hover hover;
        public Component parent;
        public List<Component> extra = new ArrayList<>(2);

        public void replaceBaseColor(LegacyFormat baseColor)
        {
            if(color == RESET)
                color = baseColor;

            extra.forEach(e-> e.replaceBaseColor(baseColor));
        }

        public void addFormat(EnumSet<LegacyFormat> format)
        {
            style.addAll(format);
        }

        public void apply(Locale locale, Object[][] args)
        {
            Map<String, Object> replacements = new HashMap<>(args.length);
            for(Object[] arg: args)
            {
                if(arg == null || arg.length < 2)
                    continue;

                String key = arg[0].toString();

                Object val = arg[1];
                if(val == null)
                    val = "null";

                if(arg.length > 2 && arg[2] instanceof Format)
                    val = ((Format)arg[2]).format(val);
                else if(val instanceof Integer || val instanceof Long || val instanceof Short || val instanceof Byte)
                    val = NumberFormat.getIntegerInstance(locale).format(val);
                else if(val instanceof Float || val instanceof Double)
                    val = NumberFormat.getNumberInstance(locale).format(val);
                else if(val instanceof Date)
                {
                    if(arg.length == 4)
                        val = DateFormat.getDateTimeInstance((int) arg[2], (int) arg[3], locale).format(val);
                    else if(arg.length == 3)
                        val = DateFormat.getDateInstance((int)arg[2], locale).format(val);
                    else
                        val = DateFormat.getDateInstance(DateFormat.DEFAULT, locale).format(val);
                }

                if(!(val instanceof String) && !(val instanceof Message))
                    val = val.toString();

                replacements.put(key, val);
            }

            replace(replacements);
        }

        public void replace(Map<String, Object> args)
        {
            if(click != null)
                click.replace(args);

            if(hover != null)
                hover.replace(args);

            extra.forEach(c-> c.replace(args));
        }

        @Override
        protected Component clone() throws CloneNotSupportedException
        {
            Component clone = (Component) super.clone();
            clone.color = color;
            clone.style = EnumSet.copyOf(style);
            if(click != null)
                clone.click = click.clone();
            if(hover != null)
                clone.hover = hover.clone();

            clone.extra = new ArrayList<>(extra.size());
            for(Component component : extra)
                clone.extra.add(component.clone());

            return clone;
        }

        protected abstract String legacyValue();

        public LegacyFormat displayColor()
        {
            LegacyFormat expectedColor = color;
            Component parent = this.parent;
            while(expectedColor == null && parent != null)
            {
                expectedColor = parent.color;
                parent = parent.parent;
            }

            if(expectedColor == null)
                expectedColor = RESET;

            return expectedColor;
        }

        public EnumSet<LegacyFormat> parentStyle()
        {
            EnumSet<LegacyFormat> parentStyle = EnumSet.noneOf(LegacyFormat.class);
            Component parent = this.parent;
            while(parent != null)
            {
                parentStyle.addAll(parent.style);
                parent = parent.parent;
            }

            return parentStyle;
        }

        public LegacyFormat parentColor()
        {
            LegacyFormat parentColor = null;
            Component parent = this.parent;
            while(parent != null)
            {
                parentColor = parent.color;
                parent = parent.parent;
            }

            if(parentColor == null)
                parentColor = RESET;

            return parentColor;
        }

        public EnumSet<LegacyFormat> displayFormat()
        {
            EnumSet<LegacyFormat> format = EnumSet.copyOf(this.style);
            format.addAll(parentStyle());
            return format;
        }

        @Override
        public String toString()
        {
            String value = legacyValue();
            if(value.isEmpty() && extra.isEmpty())
                return value;

            LegacyFormat expectedColor = displayColor();
            EnumSet<LegacyFormat> parentStyle = parentStyle();
            LegacyFormat parentColor = parentColor();

            EnumSet<LegacyFormat> addedStyle = EnumSet.copyOf(this.style);
            addedStyle.removeAll(parentStyle);

            EnumSet<LegacyFormat> fullStyle = EnumSet.copyOf(parentStyle);
            fullStyle.addAll(addedStyle);

            StringBuilder sb = new StringBuilder();

            if(color != null && parentColor != color)
            {
                sb.append(color);
                fullStyle.forEach(sb::append);
            }
            else if(!addedStyle.isEmpty())
                addedStyle.forEach(sb::append);

            EnumSet<LegacyFormat> currentStyle = EnumSet.copyOf(fullStyle);

            sb.append(value);

            for(Component component : extra)
            {
                sb.append(component);

                if(component.color != null && component.color != expectedColor)
                {
                    sb.append(expectedColor);
                    fullStyle.forEach(sb::append);
                }

                if(!component.style.isEmpty())
                {
                    currentStyle.addAll(component.style);
                    if(!currentStyle.equals(fullStyle))
                    {
                        sb.append(expectedColor);
                        fullStyle.forEach(sb::append);

                        currentStyle.clear();
                        currentStyle.addAll(fullStyle);
                    }
                }
            }
            return sb.toString();
        }
    }

    protected final class TextComponent extends Component
    {
        public String text;
        public SortedMap<Integer, String> tokens = new TreeMap<>(Comparator.reverseOrder());

        public TextComponent(String text)
        {
            this.text = text;
        }

        @Override
        protected TextComponent clone()
        {
            try
            {
                TextComponent clone = (TextComponent) super.clone();
                clone.tokens = new TreeMap<>(tokens);
                return clone;
            }
            catch(CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void replace(Map<String, Object> args)
        {
            super.replace(args);

            if(tokens.isEmpty())
                return;

            LegacyFormat baseColor = displayColor();
            EnumSet<LegacyFormat> format = displayFormat();

            StringBuilder sb = new StringBuilder(text);
            for(Map.Entry<Integer, String> entry : tokens.entrySet())
            {
                String t = entry.getValue();
                Object val = args.getOrDefault(t, "${" + t + "}");
                if(val instanceof Message)
                {
                    Message msg = (Message) val;
                    Component component = messages.get(msg.getId());
                    try
                    {
                        if(component != null)
                            component = component.clone();
                        else
                            val = component = parse(msg.getFallback());
                    }
                    catch(SAXException | CloneNotSupportedException e)
                    {
                        throw new RuntimeException(e);
                    }

                    component.replaceBaseColor(baseColor);
                    component.addFormat(format);

                    if(component.displayColor() != baseColor || !format.containsAll(component.style))
                    {
                        StringBuilder sb2 = new StringBuilder(component.toString());
                        sb2.append(baseColor);
                        format.forEach(sb2::append);
                        val = sb2;
                    }
                }
                sb.insert(entry.getKey(), val);
            }
            tokens.clear();
            String text = sb.toString();
            if(!text.contains(Character.toString(MARK)))
            {
                this.text = text;
                return;
            }

            TextComponent subStructure = parseText(text);
            this.text = "";
            extra.add(0, subStructure);
        }

        @Override
        protected String legacyValue()
        {
            StringBuilder sb = new StringBuilder(text);
            tokens.forEach((i,t)-> sb.insert(i, "${"+t+"}"));
            return sb.toString();
        }
    }

    protected abstract class Click implements Cloneable
    {
        @Override
        protected Click clone() throws CloneNotSupportedException
        {
            return (Click) super.clone();
        }

        public final void replace(Map<String, Object> replacements)
        {
            Map<String, String> stringMap = new HashMap<>(replacements.size());
            replacements.forEach((k,v)-> stringMap.put(k, v instanceof Message? toSimpleText((Message)v) : String.valueOf(v)));
            replaceStrings(stringMap);
        }

        public abstract void replaceStrings(Map<String, String> replacements);
    }

    protected final class ClickCommand extends Click
    {
        public String value;
        public ClickAction action;

        public ClickCommand(ClickAction action, String value)
        {
            this.action = action;
            this.value = value;
        }

        @Override
        public void replaceStrings(Map<String, String> replacements)
        {
            value = replaceTokens(value, replacements);
        }

        @Override
        protected ClickCommand clone()
        {
            try
            {
                return (ClickCommand) super.clone();
            }
            catch(CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected enum ClickAction
    {
        RUN, SUGGEST, OPEN_URL
    }

    protected abstract class Hover implements Cloneable
    {
        @Override
        protected Hover clone() throws CloneNotSupportedException
        {
            return (Hover) super.clone();
        }

        public void replace(Map<String, Object> replacements)
        {
            Map<String, String> stringMap = new HashMap<>(replacements.size());
            replacements.forEach((k,v)-> stringMap.put(k, v instanceof Message? toSimpleText((Message)v) : String.valueOf(v)));
            replaceStrings(stringMap);
        }

        public void replaceStrings(Map<String, String> replacements)
        {}
    }

    protected final class HoverMessage extends Hover
    {
        public TextComponent message;

        public HoverMessage(TextComponent message)
        {
            this.message = message;
        }

        @Override
        public void replace(Map<String, Object> replacements)
        {
            message.replace(replacements);
        }

        @Override
        protected Hover clone()
        {
            try
            {
                HoverMessage clone = (HoverMessage) super.clone();
                clone.message = message.clone();
                return clone;
            }
            catch(CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected final class HoverEntity extends Hover
    {
        public TextComponent name;
        public String type;
        public String id;

        public HoverEntity(String id, String type, TextComponent name)
        {
            this.id = id;
            this.type = type;
            this.name = name;
        }

        @Override
        public void replace(Map<String, Object> replacements)
        {
            super.replace(replacements);
            name.replace(replacements);
        }

        @Override
        public void replaceStrings(Map<String, String> replacements)
        {
            type = replaceTokens(type, replacements);
            id = replaceTokens(id, replacements);
        }

        @Override
        protected HoverEntity clone()
        {
            try
            {
                HoverEntity clone = (HoverEntity) super.clone();
                clone.name = name.clone();
                return clone;
            }
            catch(CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected final class HoverAchievement extends Hover
    {
        public String id;

        public HoverAchievement(String id)
        {
            this.id = id;
        }

        @Override
        public void replaceStrings(Map<String, String> replacements)
        {
            id = replaceTokens(id, replacements);
        }

        @Override
        protected HoverAchievement clone()
        {
            try
            {
                return (HoverAchievement) super.clone();
            }
            catch(CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
















