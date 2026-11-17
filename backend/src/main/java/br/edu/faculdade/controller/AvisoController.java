package br.edu.faculdade.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/avisos")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"}, allowCredentials="true")
public class AvisoController {
    private final AuthController auth;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final java.nio.file.Path arquivo=java.nio.file.Path.of("data","avisos.json");
    private final Map<Long,Aviso> avisos=new ConcurrentHashMap<>();
    private final AtomicLong sequencia=new AtomicLong();
    private br.edu.faculdade.service.BancoProjeto banco;
    private java.time.Clock relogio=java.time.Clock.system(java.time.ZoneId.of("America/Sao_Paulo"));
    @org.springframework.beans.factory.annotation.Autowired
    public AvisoController(AuthController auth,com.fasterxml.jackson.databind.ObjectMapper json,
            org.springframework.beans.factory.ObjectProvider<br.edu.faculdade.service.BancoProjeto> provider){
        this(auth,json);
        banco=provider.getIfAvailable();
        if(banco!=null){
            banco.importar("avisos-v1",()->banco.avisos(avisos.values()));
            avisos.clear();
            for(var aviso:banco.avisos()){avisos.put(aviso.id(),aviso);sequencia.accumulateAndGet(aviso.id(),Math::max);}
        }
    }
    private static final Set<String> CURSOS=Set.of("Geral","Administração","Ciência da Computação",
            "Ciências Contábeis","Educação Física","Engenharia Civil","Pedagogia","Psicologia",
            "Análise e Desenvolvimento de Sistemas");
    public record Aviso(Long id,String titulo,String descricao,LocalDate data,String curso) {}
    public AvisoController(AuthController auth,com.fasterxml.jackson.databind.ObjectMapper json) {
        this.auth=auth;
        this.json=json;
        if(java.nio.file.Files.exists(arquivo)) {
            try {
                for(var aviso:json.readValue(arquivo.toFile(),Aviso[].class)) {
                    avisos.put(aviso.id(),aviso);
                    sequencia.accumulateAndGet(aviso.id(),Math::max);
                }
            } catch(java.io.IOException e) { throw new IllegalStateException("Não foi possível carregar os avisos.",e); }
        }
    }
    @GetMapping
    public synchronized List<Aviso> listar(@RequestParam(defaultValue="ativos") String situacao,HttpServletRequest request) {
        auth.exigirUsuario(request);
        if(!Set.of("ativos","expirados","todos").contains(situacao))throw new IllegalArgumentException("Situação do aviso inválida.");
        if(!situacao.equals("ativos"))auth.exigirEditorAvisos(request);
        var hoje=LocalDate.now(relogio);
        return avisos.values().stream()
                .filter(a->situacao.equals("todos")||(situacao.equals("expirados")?a.data().isBefore(hoje):!a.data().isBefore(hoje)))
                .sorted(Comparator.comparing(Aviso::data).reversed()
                .thenComparing(Aviso::id,Comparator.reverseOrder())).toList();
    }
    @PostMapping
    public synchronized Aviso criar(@RequestBody Aviso dados,HttpServletRequest request) {
        auth.exigirEditorAvisos(request);
        var aviso=validar(sequencia.incrementAndGet(),dados);
        var novos=new HashMap<>(avisos);
        novos.put(aviso.id(),aviso);
        salvar(novos);
        return aviso;
    }
    @PutMapping("/{id}")
    public synchronized Aviso editar(@PathVariable Long id,@RequestBody Aviso dados,HttpServletRequest request) {
        auth.exigirEditorAvisos(request);
        var aviso=validar(id,dados);
        if(!avisos.containsKey(id))throw new NoSuchElementException("Aviso não encontrado.");
        var novos=new HashMap<>(avisos);
        novos.put(id,aviso);
        salvar(novos);
        return aviso;
    }
    @DeleteMapping("/{id}")
    public synchronized void excluir(@PathVariable Long id,HttpServletRequest request) {
        auth.exigirEditorAvisos(request);
        var novos=new HashMap<>(avisos);
        novos.remove(id);
        salvar(novos);
    }
    private void salvar(Map<Long,Aviso> novos) {
        if(banco!=null){banco.avisos(novos.values());avisos.clear();avisos.putAll(novos);return;}
        try {
            java.nio.file.Files.createDirectories(arquivo.getParent());
            var temporario=arquivo.resolveSibling("avisos.tmp");
            json.writeValue(temporario.toFile(),novos.values());
            java.nio.file.Files.move(temporario,arquivo,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            avisos.clear();
            avisos.putAll(novos);
        } catch(java.io.IOException e) { throw new IllegalStateException("Não foi possível salvar o aviso.",e); }
    }
    private Aviso validar(Long id,Aviso a) {
        if(a.titulo()==null||a.titulo().isBlank()||a.descricao()==null||a.descricao().isBlank()
                ||a.data()==null||!CURSOS.contains(a.curso()==null?"":a.curso()))
            throw new IllegalArgumentException("Preencha título, descrição, data de expiração e curso.");
        if(!a.data().isAfter(LocalDate.now(relogio)))
            throw new IllegalArgumentException("Escolha uma data de expiração a partir de amanhã.");
        return new Aviso(id,a.titulo().trim(),a.descricao().trim(),a.data(),a.curso());
    }
}
