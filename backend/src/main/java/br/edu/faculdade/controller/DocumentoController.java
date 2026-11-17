package br.edu.faculdade.controller;

import br.edu.faculdade.service.DocumentoStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/documentos")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"},allowCredentials="true")
@RequiredArgsConstructor
public class DocumentoController {
    private final AuthController auth;
    private final DocumentoStorageService storage;
    private final br.edu.faculdade.service.GoogleDriveConteudoService sincronizacao;
    public record PastaReq(String nome,String paiId) {}
    @GetMapping public DocumentoStorageService.Lista listar(@RequestParam(required=false) String pastaId,HttpServletRequest request) throws IOException {
        return storage.listar(auth.exigirUsuario(request).getId(),pastaId);
    }
    @PostMapping("/pastas") public DocumentoStorageService.Item criar(@RequestBody PastaReq dados,HttpServletRequest request) throws IOException {
        long usuario=auth.exigirUsuario(request).getId();
        var item=storage.criarPasta(usuario,dados.nome(),dados.paiId());
        sincronizacao.pendente(usuario);
        return item;
    }
    @PostMapping("/arquivos") public DocumentoStorageService.Item enviar(@RequestParam MultipartFile arquivo,@RequestParam(required=false) String pastaId,HttpServletRequest request) throws IOException {
        long usuario=auth.exigirUsuario(request).getId();
        var item=storage.enviar(usuario,arquivo,pastaId);
        sincronizacao.pendente(usuario);
        return item;
    }
    @GetMapping("/arquivos/{id}") public ResponseEntity<byte[]> baixar(@PathVariable String id,HttpServletRequest request) throws IOException {
        long usuario=auth.exigirUsuario(request).getId();
        var item=storage.obter(usuario,id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition",ContentDisposition.attachment().filename(item.nome(),StandardCharsets.UTF_8).build().toString())
                .header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff")
                .body(storage.baixar(usuario,id));
    }
    @DeleteMapping("/{tipo:pastas|arquivos}/{id}") public void excluir(@PathVariable String id,HttpServletRequest request) throws IOException {
        long usuario=auth.exigirUsuario(request).getId();
        storage.excluir(usuario,id);
        sincronizacao.exclusaoPendente(usuario);
    }
}
