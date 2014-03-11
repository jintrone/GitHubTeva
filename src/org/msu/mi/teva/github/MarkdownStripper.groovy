package org.msu.mi.teva.github

import groovy.util.logging.Log4j
import org.pegdown.Extensions
import org.pegdown.PegDownProcessor
import org.pegdown.Printer
import org.pegdown.ast.AbbreviationNode
import org.pegdown.ast.AutoLinkNode
import org.pegdown.ast.BlockQuoteNode
import org.pegdown.ast.BulletListNode
import org.pegdown.ast.CodeNode
import org.pegdown.ast.DefinitionListNode
import org.pegdown.ast.DefinitionNode
import org.pegdown.ast.DefinitionTermNode
import org.pegdown.ast.ExpImageNode
import org.pegdown.ast.ExpLinkNode
import org.pegdown.ast.HeaderNode
import org.pegdown.ast.HtmlBlockNode
import org.pegdown.ast.InlineHtmlNode
import org.pegdown.ast.ListItemNode
import org.pegdown.ast.MailLinkNode
import org.pegdown.ast.OrderedListNode
import org.pegdown.ast.ParaNode
import org.pegdown.ast.QuotedNode
import org.pegdown.ast.RefImageNode
import org.pegdown.ast.RefLinkNode
import org.pegdown.ast.ReferenceNode
import org.pegdown.ast.RootNode
import org.pegdown.ast.SimpleNode
import org.pegdown.ast.SpecialTextNode
import org.pegdown.ast.StrikeNode
import org.pegdown.ast.StrongEmphSuperNode
import org.pegdown.ast.SuperNode
import org.pegdown.ast.TableBodyNode
import org.pegdown.ast.TableCaptionNode
import org.pegdown.ast.TableCellNode
import org.pegdown.ast.TableColumnNode
import org.pegdown.ast.TableHeaderNode
import org.pegdown.ast.TableNode
import org.pegdown.ast.TableRowNode
import org.pegdown.ast.TextNode
import org.pegdown.ast.VerbatimNode
import org.pegdown.ast.Visitor
import org.pegdown.ast.WikiLinkNode

/**
 * Created by josh on 1/22/14.
 */
@Log4j
class MarkdownStripper implements Visitor{

    StringBuilder printer
    protected final Map<String, ReferenceNode> references = new HashMap<String, ReferenceNode>()
    protected final Map<String, String> abbreviations = new HashMap<String, String>()
    PegDownProcessor processor = new PegDownProcessor(Extensions.AUTOLINKS | Extensions.FENCED_CODE_BLOCKS)

    MarkdownStripper() {
        printer = new StringBuilder()
        printer.metaClass.clear = {this.printer.setLength(0)}

    }


    protected TableNode currentTableNode;
    protected int currentTableColumn;
    protected boolean inTableHeader;

    String stripMarkdown(String input) {
        printer.clear()
        RootNode root =  processor.parseMarkdown(input.toCharArray())

        root.accept(this)

        return printer.toString()


    }

    @Override
    void visit(RootNode node) {
        for (ReferenceNode refNode : node.getReferences()) {
            visitChildren(refNode);
            references.put(normalize(printer.toString()), refNode);
            printer.clear()
        }
        for (AbbreviationNode abbrNode : node.getAbbreviations()) {
            log.info("Discovered abbreviation!")
            visitChildren(abbrNode);
            String abbr = printer.toString();
            printer.clear();
            abbrNode.getExpansion().accept(this);
            String expansion = printer.toString();
            abbreviations.put(abbr, expansion);
            printer.clear();
        }
        visitChildren(node);
    }

    protected String normalize(String string) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch(c) {
                case ' ':
                case '\n':
                case '\t':
                    continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    @Override
    void visit(AbbreviationNode node) {

    }

    @Override
    void visit(AutoLinkNode node) {
        //ignore auto links
    }

    @Override
    void visit(BlockQuoteNode node) {
        printer << " "
        visitChildren(node)
        printer << " "
    }

    @Override
    void visit(BulletListNode node) {

    }

    @Override
    void visit(CodeNode node) {
        //ignore code
    }

    @Override
    void visit(DefinitionListNode node) {
        visitChildren(node)
    }

    @Override
    void visit(DefinitionNode node) {
        visitChildren(node)
    }

    @Override
    void visit(DefinitionTermNode node) {
        visitChildren(node)
    }

    @Override
    void visit(ExpImageNode node) {
        //ignore
    }

    @Override
    void visit(ExpLinkNode node) {
        //should just add text of link
        visitChildren(node)
    }

    @Override
    void visit(HeaderNode node) {
        printer << " "
        visitChildren(node)
        printer << " "
    }

    @Override
    void visit(HtmlBlockNode node) {
        String text = node.getText();
        if (text.length() > 0) printer << " "
        printer << text
    }

    @Override
    void visit(InlineHtmlNode node) {
        printer << node.getText()
    }

    @Override
    void visit(ListItemNode node) {
       printer << " "
        visitChildren(node)
        printer << " "
    }

    @Override
    void visit(MailLinkNode node) {
        //ignore mail links
    }

    @Override
    void visit(OrderedListNode node) {
        printer << " "
        visitChildren(node)
        printer << " "
    }

    @Override
    void visit(ParaNode node) {
        printer << " "
        visitChildren(node)
        printer << " "
    }

    @Override
    void visit(QuotedNode node) {
        visitChildren(node)
    }

    @Override
    void visit(ReferenceNode node) {
       //ignore
    }

    @Override
    void visit(RefImageNode node) {
        //skip image
        visitChildren(node)
    }

    @Override
    void visit(RefLinkNode node) {
       //skip link
        visitChildren(node)
    }



    @Override
    void visit(SimpleNode node) {
        printer << " "
    }

    @Override
    void visit(SpecialTextNode node) {
        printer << node.getText()
    }

    @Override
    void visit(StrikeNode node) {
        visitChildren(node)
    }

    @Override
    void visit(StrongEmphSuperNode node) {
        printer << " "
        if (!node.isClosed()) {
            printer << node.getChars()
        }
        visitChildren(node)
        printer << " "
    }

    @Override
    void visit(TableBodyNode node) {
         visitChildren(node)
    }

    @Override
    void visit(TableCaptionNode node) {
         visitChildren(node)
    }

    @Override
    void visit(TableCellNode node) {
       printer << " "
        visitChildren(node)
        printer << " "
    }

    @Override
    void visit(TableColumnNode node) {
         //ignore
    }

    @Override
    void visit(TableHeaderNode node) {
        printer << " "
        visitChildren(node)
        printer << " "
    }

    @Override
    void visit(TableNode node) {
        visitChildren(node)
    }

    @Override
    void visit(TableRowNode node) {
        visitChildren(node)
    }

    @Override
    void visit(VerbatimNode node) {

        printer << "\n"
        //skip code!
        //printer << node.getText()
    }

    @Override
    void visit(WikiLinkNode node) {
       printer << " ${node.getText()} "
    }

    @Override
    void visit(TextNode node) {
        printer << " ${node.getText()} "
//        if (abbreviations.isEmpty()) {
//            printer.print(node.getText());
//        } else {
//            printWithAbbreviations(node.getText());
//        }
    }

    @Override
    void visit(SuperNode node) {
        visitChildren(node)
    }

    @Override
    void visit(org.pegdown.ast.Node node) {
        throw new RuntimeException("Don't know how to handle node " + node);
    }

    protected void visitChildren(SuperNode node) {
        for (org.pegdown.ast.Node child : node.getChildren()) {
            child.accept(this);
        }
    }
}
