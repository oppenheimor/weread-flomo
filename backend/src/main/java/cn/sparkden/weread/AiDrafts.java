package cn.sparkden.weread;

import java.util.*;
import static cn.sparkden.weread.AiModels.*;

/** A bounded JSON-prefix reader for display only. Nothing here is editable or deliverable.
 * The complete answer still goes through AiOrganizer's independent strict validator. */
final class AiDrafts {
    private record Node(Object value,boolean complete) {}
    static List<Draft> read(String prefix,Snapshot snapshot) {
        if(prefix.length()>250_000) return List.of();
        String text=prefix.stripLeading();
        if(text.startsWith("```")) { int line=text.indexOf('\n'); if(line<0) return List.of(); text=text.substring(line+1); }
        Node root=new Reader(text).value(0);
        if(!(root.value() instanceof Map<?,?> object)) return List.of();
        Node groups=node(object.get("groups"));
        if(!(groups.value() instanceof List<?> list)) return List.of();
        var result=new ArrayList<Draft>();
        for(int i=0;i<Math.min(200,list.size());i++) {
            if(!(node(list.get(i)).value() instanceof Map<?,?> group)) continue;
            Node sources=node(group.get("sourceIds"));
            if(!(sources.value() instanceof List<?> ids) || !sources.complete() || ids.isEmpty()) continue;
            var indices=new HashSet<Integer>(); boolean valid=true;
            for(Object id:ids) {
                Node item=node(id); String s=item.value() instanceof String str?str:"";
                if(!item.complete() || !s.matches("0|[1-9][0-9]{0,2}")) { valid=false; break; }
                int index=Integer.parseInt(s);
                if(index>=snapshot.notes().size() || !indices.add(index)) { valid=false; break; }
            }
            if(!valid) continue;
            if(indices.size()==1) {
                var source=snapshot.notes().get(indices.iterator().next());
                result.add(new Draft("draft-"+i,"excerpt","","",source.quote(),source.thought()));
            } else {
                String title=string(group,"title",120),body=string(group,"body",20000);
                if(!title.isEmpty() || !body.isEmpty()) result.add(new Draft("draft-"+i,"viewpoint",title,body,"",""));
            }
        }
        return result;
    }
    private static Node node(Object n) { return n instanceof Node value?value:new Node(null,false); }
    private static String string(Map<?,?> object,String key,int max) {
        var value=node(object.get(key)).value();
        if(!(value instanceof String s)) return "";
        s=s.substring(0,Math.min(max,s.length()));
        if(!s.isEmpty() && Character.isHighSurrogate(s.charAt(s.length()-1))) s=s.substring(0,s.length()-1);
        return s;
    }
    private static final class Reader {
        final String s; int pos;
        Reader(String s) { this.s=s; }
        void space() { while(pos<s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        boolean take(char c) { space(); if(pos<s.length() && s.charAt(pos)==c) { pos++; return true; } return false; }
        Node value(int depth) {
            space(); if(depth>12 || pos>=s.length()) return new Node(null,false);
            char c=s.charAt(pos);
            if(c=='"') return string();
            if(c=='{') {
                pos++; Map<String,Node> values=new LinkedHashMap<>();
                for(int count=0;count<220;count++) {
                    if(take('}')) return new Node(values,true);
                    space(); if(pos>=s.length() || s.charAt(pos)!='"') break;
                    Node key=string(); if(!key.complete() || !take(':')) break;
                    Node child=value(depth+1); values.put((String)key.value(),child);
                    if(!child.complete()) break;
                    if(take('}')) return new Node(values,true);
                    if(!take(',')) break;
                }
                return new Node(values,false);
            }
            if(c=='[') {
                pos++; List<Node> values=new ArrayList<>();
                for(int count=0;count<220;count++) {
                    if(take(']')) return new Node(values,true);
                    if(pos>=s.length()) break;
                    Node child=value(depth+1); values.add(child);
                    if(!child.complete()) break;
                    if(take(']')) return new Node(values,true);
                    if(!take(',')) break;
                }
                return new Node(values,false);
            }
            // Non-string scalars aren't used for draft fields, but can occur in ignored metadata.
            int begin=pos;
            while(pos<s.length() && !",]} \r\n\t".contains(String.valueOf(s.charAt(pos)))) pos++;
            return new Node(null,pos>begin && pos<s.length());
        }
        Node string() {
            pos++; StringBuilder out=new StringBuilder();
            while(pos<s.length()) {
                char c=s.charAt(pos++);
                if(c=='"') return new Node(out.toString(),true);
                if(c=='\\') {
                    if(pos>=s.length()) break;
                    char escaped=s.charAt(pos++);
                    switch(escaped) {
                        case '"','\\','/' -> out.append(escaped);
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> {
                            if(pos+4>s.length()) return new Node(out.toString(),false);
                            try { out.append((char)Integer.parseInt(s.substring(pos,pos+4),16)); pos+=4; }
                            catch(NumberFormatException e) { return new Node(out.toString(),false); }
                        }
                        default -> { return new Node(out.toString(),false); }
                    }
                } else if(c<32) return new Node(out.toString(),false);
                else out.append(c);
            }
            return new Node(out.toString(),false);
        }
    }
}
