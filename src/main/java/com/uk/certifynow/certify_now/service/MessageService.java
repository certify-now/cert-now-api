package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Message;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.MessageDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.MessageRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    public MessageService(final MessageRepository messageRepository,
            final JobRepository jobRepository, final UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
    }

    public List<MessageDTO> findAll() {
        final List<Message> messages = messageRepository.findAll(Sort.by("id"));
        return messages.stream()
                .map(message -> mapToDTO(message, new MessageDTO()))
                .toList();
    }

    public MessageDTO get(final UUID id) {
        return messageRepository.findById(id)
                .map(message -> mapToDTO(message, new MessageDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final MessageDTO messageDTO) {
        final Message message = new Message();
        mapToEntity(messageDTO, message);
        return messageRepository.save(message).getId();
    }

    public void update(final UUID id, final MessageDTO messageDTO) {
        final Message message = messageRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(messageDTO, message);
        messageRepository.save(message);
    }

    public void delete(final UUID id) {
        final Message message = messageRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        messageRepository.delete(message);
    }

    private MessageDTO mapToDTO(final Message message, final MessageDTO messageDTO) {
        messageDTO.setId(message.getId());
        messageDTO.setIsSystem(message.getIsSystem());
        messageDTO.setCreatedAt(message.getCreatedAt());
        messageDTO.setReadAt(message.getReadAt());
        messageDTO.setBody(message.getBody());
        messageDTO.setJob(message.getJob() == null ? null : message.getJob().getId());
        messageDTO.setSender(message.getSender() == null ? null : message.getSender().getId());
        return messageDTO;
    }

    private Message mapToEntity(final MessageDTO messageDTO, final Message message) {
        message.setIsSystem(messageDTO.getIsSystem());
        message.setCreatedAt(messageDTO.getCreatedAt());
        message.setReadAt(messageDTO.getReadAt());
        message.setBody(messageDTO.getBody());
        final Job job = messageDTO.getJob() == null ? null : jobRepository.findById(messageDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
        message.setJob(job);
        final User sender = messageDTO.getSender() == null ? null : userRepository.findById(messageDTO.getSender())
                .orElseThrow(() -> new NotFoundException("sender not found"));
        message.setSender(sender);
        return message;
    }

    @EventListener(BeforeDeleteJob.class)
    public void on(final BeforeDeleteJob event) {
        final ReferencedException referencedException = new ReferencedException();
        final Message jobMessage = messageRepository.findFirstByJobId(event.getId());
        if (jobMessage != null) {
            referencedException.setKey("job.message.job.referenced");
            referencedException.addParam(jobMessage.getId());
            throw referencedException;
        }
    }

    @EventListener(BeforeDeleteUser.class)
    public void on(final BeforeDeleteUser event) {
        final ReferencedException referencedException = new ReferencedException();
        final Message senderMessage = messageRepository.findFirstBySenderId(event.getId());
        if (senderMessage != null) {
            referencedException.setKey("user.message.sender.referenced");
            referencedException.addParam(senderMessage.getId());
            throw referencedException;
        }
    }

}
