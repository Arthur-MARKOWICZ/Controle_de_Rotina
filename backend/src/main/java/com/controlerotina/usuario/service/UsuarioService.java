package com.controlerotina.usuario.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.shared.exception.CredenciaisInvalidasException;
import com.controlerotina.shared.exception.EmailJaCadastradoException;
import com.controlerotina.usuario.dto.AtualizarMeRequest;
import com.controlerotina.usuario.dto.CadastroUsuarioRequest;
import com.controlerotina.usuario.dto.UsuarioResponse;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioResponse cadastrar(CadastroUsuarioRequest request) {
        String email = normalizarEmail(request.email());

        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailJaCadastradoException(email);
        }

        Instant agora = Instant.now();
        Usuario usuario = new Usuario(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.senha()),
                request.nome().trim(),
                agora,
                agora
        );

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarMe(UUID usuarioId) {
        return UsuarioResponse.from(buscarPorId(usuarioId));
    }

    @Transactional
    public UsuarioResponse atualizarMe(UUID usuarioId, AtualizarMeRequest request) {
        Usuario usuario = buscarPorId(usuarioId);

        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        String novoNome = request.nome();
        String novoEmail = request.email() != null ? normalizarEmail(request.email()) : null;
        String novaSenhaHash = request.novaSenha() != null
                ? passwordEncoder.encode(request.novaSenha())
                : null;

        if (novoEmail != null && !novoEmail.equals(usuario.getEmail())
                && usuarioRepository.existsByEmail(novoEmail)) {
            throw new EmailJaCadastradoException(novoEmail);
        }

        usuario.atualizar(novoNome, novoEmail, novaSenhaHash, Instant.now());
        return UsuarioResponse.from(usuario);
    }

    private Usuario buscarPorId(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(CredenciaisInvalidasException::new);
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }
}
