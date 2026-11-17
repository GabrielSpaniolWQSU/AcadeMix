package br.edu.faculdade;

@org.springframework.context.annotation.Configuration
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.agendamentos",havingValue="true",matchIfMissing=true)
public class AgendamentosConfig {}
