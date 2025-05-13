import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ForthCompiler {
    private static final Map<String, String> WORDS = new HashMap<>();
    static {
        WORDS.put("+", "pop rbx\npop rax\nadd rax, rbx\npush rax");
        WORDS.put("-", "pop rbx\npop rax\nsub rax, rbx\npush rax");
        WORDS.put("*", "pop rbx\npop rax\nimul rax, rbx\npush rax");
        WORDS.put("mod", "pop rbx\npop rax\ncqo\nidiv rbx\npush rdx");
        WORDS.put("neg", "pop rax\nneg rax\npush rax");
        WORDS.put("dup", "pop rax\npush rax\npush rax");
        WORDS.put("swap", "pop rax\npop rbx\npush rax\npush rbx");
        WORDS.put("drop", "pop rax");
        WORDS.put("over", "pop rax\npop rbx\npush rbx\npush rax\npush rbx");
        WORDS.put("nip", "pop rax\npop rbx\npush rax");
        WORDS.put("tack", "pop rax\npop rbx\npush rax\npush rbx\npush rax");
        WORDS.put(".", "pop rdi\ncall print_int");
        WORDS.put(".s",
                "push rbx\nmov rbx, stack\n1:\ncmp rbx, rsp\njae 2f\nmov rdi, [rbx]\ncall print_int\nadd rbx, 8\njmp 1b\n2:\npop rbx");
    }

    private static String asm(List<String> src) {
        StringBuilder a = new StringBuilder();
        a.append(".intel_syntax noprefix\n.text\n.global _start\n_start:\ncall main\nmov rdi,0\nmov rax,60\nsyscall\n");
        a.append("print_int:\nmov rax,rdi\nmov rcx,buf+20\nmov rbx,10\n3:\nxor rdx,rdx\ndiv rbx\ndec rcx\nadd dl,'0'\nmov [rcx],dl\ntest rax,rax\njnz 3b\nmov byte ptr [rcx-1],10\nmov rdx,buf+21\nsub rdx,rcx\nmov rsi,rcx\nmov rdi,1\nmov rax,1\nsyscall\nret\n");
        a.append(".bss\nbuf resb 21\nstack resq 1024\nvars  resq 256\n");
        Map<String, Integer> vars = new LinkedHashMap<>();
        int v = 0;
        List<String> b = new ArrayList<>();
        b.add("main:");
        b.add("mov rsp, stack+8192");
        List<String> tok = new ArrayList<>();
        for (String line : src) {
            int c = line.indexOf('\\');
            if (c >= 0) line = line.substring(0, c);
            for (String t : line.trim().split("\\s+")) if (!t.isEmpty()) tok.add(t);
        }
        for (int i = 0; i < tok.size(); i++) {
            String t = tok.get(i);
            if (t.matches("\\d+")) {
                b.add("mov rax," + t);
                b.add("push rax");
            } else if (WORDS.containsKey(t)) {
                for (String s : WORDS.get(t).split("\n")) b.add(s);
            } else if ("variable".equals(t)) {
                vars.put(tok.get(++i), v++);
            } else if ("!".equals(t)) {
                b.add("pop rbx");
                b.add("pop rax");
                b.add("mov [vars+rbx*8],rax");
            } else if ("@".equals(t)) {
                b.add("pop rax");
                b.add("mov rax,[vars+rax*8]");
                b.add("push rax");
            } else if (vars.containsKey(t)) {
                b.add("mov rax," + vars.get(t));
                b.add("push rax");
            } else {
                throw new IllegalArgumentException("Unknown token: " + t);
            }
        }
        b.add("ret");
        for (String s : b) a.append(s).append('\n');
        return a.toString();
    }

    private static void compile(String in) throws IOException, InterruptedException {
        String s = in.replaceFirst("\\.fs$", "");
        List<String> lines = Files.readAllLines(Paths.get(in));
        Files.writeString(Paths.get(s + ".s"), asm(lines));
        new ProcessBuilder("as", "-o", s + ".o", s + ".s").inheritIO().start().waitFor();
        new ProcessBuilder("ld", "-o", s, s + ".o").inheritIO().start().waitFor();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1){
            System.exit(1);
        }
        compile(args[0]);
    }
}
