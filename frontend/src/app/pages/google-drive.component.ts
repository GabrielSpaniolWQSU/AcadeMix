import { HttpClient } from '@angular/common/http';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../services/api.service';

@Component({
  standalone: true,
  template: `
    <section>
      <h1>Google Drive</h1>
      @if (mensagem) { <p role="status">{{ mensagem }}</p> }
      @if (carregando) {
        <p>Verificando a conexão…</p>
      } @else if (status?.conectado) {
        <p>Conta Google conectada. O sistema lembrará a conexão após reiniciar.</p>
        @if (!status?.escrita) {
          <p>Autorize a criação de pastas para preparar a pasta individual de cada usuário nesta conta Google.</p>
          <a class="button" [href]="api.base + '/google-drive/conectar'">Autorizar criação de pastas</a>
        }
        <h2>Pastas dos usuários</h2>
        <p>Gestão Acadêmica → nome do usuário → Documentos e Atas e Atividades</p>
        @if (pastas) {
          <p role="status">{{pastas.mensagem}}</p>
          <p>{{pastas.usuariosPreparados}} usuário(s) com pastas preparadas.</p>
          @if (pastas.pastaPrincipal) {
            <p><a [href]="'https://drive.google.com/drive/folders/' + pastas.pastaPrincipal" target="_blank" rel="noopener noreferrer">Abrir pasta principal no Drive</a></p>
          }
        }
        <button type="button" class="secondary" [disabled]="!status?.escrita || preparando" (click)="preparar()">Preparar pastas dos usuários</button>
        <p><small>Novos cadastros são verificados automaticamente a cada 30 segundos.</small></p>
        <button type="button" [disabled]="desconectando" (click)="desconectar()">Desconectar</button>
      } @else if (status?.configurado) {
        <p>Escolha a conta que você quer usar com o sistema.</p>
        <a class="button" [href]="api.base + '/google-drive/conectar'">Conectar Google Drive</a>
      } @else if (status) {
        <p>Execute configurar-google-drive.bat na pasta do projeto e reinicie o sistema.</p>
      }
    </section>
  `
})
export class GoogleDriveComponent implements OnInit, OnDestroy {
  status?: { configurado: boolean; conectado: boolean; escrita?:boolean };
  pastas?: {mensagem:string;usuariosPreparados:number;pastaPrincipal:string|null};
  preparando=false;
  private temporizador?:ReturnType<typeof setInterval>;
  carregando = true;
  desconectando = false;
  mensagem = '';

  constructor(public api: ApiService, private http: HttpClient, private rota: ActivatedRoute) {}

  ngOnInit(): void {
    const mensagens: Record<string, string> = {
      cancelado: 'Você cancelou a autorização no Google.',
      'sessao-expirada': 'A autorização expirou. Clique em Conectar Google Drive para recomeçar.',
      'sem-permissao': 'O Google Drive não foi autorizado. Tente conectar novamente.',
      'sem-configuracao': 'As credenciais do Google Drive ainda não foram configuradas.',
      falha: 'Não foi possível concluir a conexão com o Google. Tente novamente.'
    };
    const resultado = this.rota.snapshot.queryParamMap.get('resultado');
    this.mensagem = resultado && Object.hasOwn(mensagens, resultado) ? mensagens[resultado] : '';
    this.http.get<{ configurado: boolean; conectado: boolean; escrita:boolean }>(this.api.base + '/google-drive/status',
      { withCredentials: true }).subscribe({
      next: status => {
        this.status = status; this.carregando = false;
        if(status.conectado){
          this.atualizarPastas();
          this.temporizador=setInterval(()=>this.atualizarPastas(),10000);
        }
      },
      error: erro => {
        this.mensagem = erro.error?.erro || 'Não foi possível verificar a conexão. Confira se o backend está rodando.';
        if(erro.status===502)this.status={configurado:true,conectado:false};
        this.carregando = false;
      }
    });
  }
  ngOnDestroy(){if(this.temporizador)clearInterval(this.temporizador)}
  private atualizarPastas(){
    this.http.get<{mensagem:string;usuariosPreparados:number;pastaPrincipal:string|null}>(
      this.api.base+'/google-drive/pastas-usuarios',{withCredentials:true}).subscribe({
        next:pastas=>this.pastas=pastas,
        error:()=>{this.mensagem='Não foi possível consultar a preparação das pastas.'}
      });
  }
  preparar(){
    this.preparando=true;
    this.http.post(this.api.base+'/google-drive/pastas-usuarios',{}, {withCredentials:true}).subscribe({
      next:()=>{this.mensagem='Preparação solicitada. Acompanhe o resultado abaixo.';this.preparando=false},
      error:()=>{this.mensagem='Não foi possível solicitar a preparação.';this.preparando=false}
    });
  }

  desconectar(): void {
    this.desconectando = true;
    this.http.delete(this.api.base + '/google-drive/conexao', { withCredentials: true }).subscribe({
      next: () => {
        if(this.temporizador)clearInterval(this.temporizador);
        if (this.status) this.status.conectado = false;
        this.mensagem = 'Conta desconectada deste sistema.';
        this.desconectando = false;
      },
      error: () => {
        this.mensagem = 'Não foi possível desconectar a conta. Tente novamente.';
        this.desconectando = false;
      }
    });
  }
}
